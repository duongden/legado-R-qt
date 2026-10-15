import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import ts from 'typescript'

const source = readFileSync(new URL('../src/store/readProgressSync.ts', import.meta.url), 'utf8')
const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText
const { ReadProgressSync } = await import(`data:text/javascript;base64,${Buffer.from(js).toString('base64')}`)

const snapshot = (index = 2, pos = 40, revision = 100) => ({ bookUrl: 'book', progress: {
  name: 'Truyện', author: 'Tác giả', durChapterIndex: index, durChapterPos: pos,
  durChapterTime: revision, durChapterTitle: `Chương ${index + 1}`,
} })
const position = (index, pos = 0) => ({ durChapterIndex: index, durChapterPos: pos })
function setup() {
  let server = snapshot()
  let offline = false
  const writes = [], remotes = [], statuses = []
  const sync = new ReadProgressSync({
    read: async () => { if (offline) throw Error('offline'); return structuredClone(server) },
    write: async (value, keepalive) => {
      writes.push({ ...value, keepalive })
      if (offline) throw Error('offline')
      const accepted = value.baseRevision === server.progress.durChapterTime
      if (accepted) server = snapshot(value.durChapterIndex, value.durChapterPos, value.baseRevision + 1)
      return { accepted, snapshot: structuredClone(server) }
    },
  }, (value, conflict) => remotes.push({ ...value, conflict }), status => statuses.push(status))
  return { sync, writes, remotes, statuses, get server() { return server },
    movePhone: (index, pos = 0) => { server = snapshot(index, pos, server.progress.durChapterTime + 1) },
    setOffline: value => { offline = value } }
}

test('browser opens at phone position and saves chapter five with acknowledged revision', async () => {
  const t = setup(); await t.sync.refresh()
  assert.equal(t.remotes[0].progress.durChapterIndex, 2)
  t.sync.update(position(4, 80)); await t.sync.flush()
  assert.equal(t.server.progress.durChapterIndex, 4)
  assert.equal(t.writes[0].baseRevision, 100)
  assert.equal(t.statuses.at(-1), 'saved')
})

test('focus refresh picks up further phone reading without sending the old browser position', async () => {
  const t = setup(); await t.sync.refresh(); t.movePhone(5, 60); await t.sync.refresh()
  assert.equal(t.remotes.at(-1).progress.durChapterIndex, 5)
  assert.equal(t.writes.length, 0)
})

test('stale tab receives canonical position instead of overwriting the phone', async () => {
  const t = setup(); await t.sync.refresh(); t.movePhone(5)
  t.sync.update(position(3)); await t.sync.flush()
  assert.equal(t.server.progress.durChapterIndex, 5)
  assert.equal(t.remotes.at(-1).conflict, true)
})

test('intentional move backwards is saved', async () => {
  const t = setup(); await t.sync.refresh(); t.sync.update(position(1)); await t.sync.flush()
  assert.equal(t.server.progress.durChapterIndex, 1)
})

test('offline writes stay pending and retry after reconnect', async () => {
  const t = setup(); await t.sync.refresh(); t.setOffline(true)
  t.sync.update(position(4)); await t.sync.flush()
  assert.equal(t.statuses.at(-1), 'offline')
  t.setOffline(false); await t.sync.refresh()
  assert.equal(t.server.progress.durChapterIndex, 4)
  assert.equal(t.statuses.at(-1), 'saved')
})

test('an offline browser cannot overwrite newer phone progress after reconnecting', async () => {
  const t = setup(); await t.sync.refresh(); t.setOffline(true)
  t.sync.update(position(4)); await t.sync.flush(); t.movePhone(6)
  t.setOffline(false); await t.sync.refresh()
  assert.equal(t.server.progress.durChapterIndex, 6)
  assert.equal(t.remotes.at(-1).conflict, true)
})

test('serialize writes and send the newest position with the previous acknowledgement', async () => {
  let resolveFirst
  const calls = []
  const sync = new ReadProgressSync({ read: async () => snapshot(), write: async value => {
    calls.push(value)
    if (calls.length === 1) await new Promise(resolve => { resolveFirst = resolve })
    return { accepted: true, snapshot: snapshot(value.durChapterIndex, value.durChapterPos, value.baseRevision + 1) }
  } }, () => {}, () => {})
  await sync.refresh(); sync.update(position(3)); const sending = sync.flush()
  sync.update(position(4)); assert.equal(sync.flush(), sending); assert.equal(calls.length, 1)
  resolveFirst(); await sending
  assert.deepEqual(calls.map(v => [v.durChapterIndex, v.baseRevision]), [[3, 100], [4, 101]])
})

test('restoring the same position does not create a write; closing uses keepalive', async () => {
  const t = setup(); await t.sync.refresh(); t.sync.update(position(2, 40)); await t.sync.flush()
  assert.equal(t.writes.length, 0)
  t.sync.update(position(4)); await t.sync.flush(true)
  assert.equal(t.writes.at(-1).keepalive, true)
})

test('a stopped session cannot overwrite a newly opened book', async () => {
  const t = setup(); await t.sync.refresh(); t.sync.update(position(4)); t.sync.stop(); await t.sync.flush()
  assert.equal(t.writes.length, 0)
})

test('a delayed refresh cannot roll back a write acknowledged while the GET was in flight', async () => {
  let resolveRead
  let reads = 0
  const transport = {
    read: async () => {
      if (++reads === 1) return snapshot()
      return new Promise(resolve => { resolveRead = resolve })
    },
    write: async value => ({ accepted: true, snapshot: snapshot(value.durChapterIndex, value.durChapterPos, 101) }),
  }
  const sync = new ReadProgressSync(transport, () => {}, () => {})
  await sync.refresh()
  const refreshing = sync.refresh()
  await new Promise(resolve => setImmediate(resolve))
  sync.update(position(4)); await sync.flush()
  resolveRead(snapshot()); await refreshing
  sync.update(position(5))
  // A second write must still use the acknowledged version, never the delayed GET's 100.
  let usedRevision
  transport.write = async value => {
    usedRevision = value.baseRevision
    return { accepted: true, snapshot: snapshot(5, 0, 102) }
  }
  await sync.flush()
  assert.equal(usedRevision, 101)
})
