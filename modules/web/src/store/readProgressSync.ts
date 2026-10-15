import type { BookProgress } from '../book'

export type ProgressSnapshot = { bookUrl: string; progress: BookProgress }
export type ProgressResult = { accepted: boolean; snapshot: ProgressSnapshot }
export type SyncStatus = 'loading' | 'saved' | 'pending' | 'saving' | 'offline'
type Position = { durChapterIndex: number; durChapterPos: number }
type Transport = {
  read: () => Promise<ProgressSnapshot>
  write: (position: Position & { baseRevision: number }, keepalive: boolean) => Promise<ProgressResult>
}

const samePosition = (a: Position, b: Position) =>
  a.durChapterIndex === b.durChapterIndex && a.durChapterPos === b.durChapterPos

/** One queue per open book. Never derive revisions from the browser clock. */
export class ReadProgressSync {
  private canonical?: ProgressSnapshot
  private pending?: Position
  private running?: Promise<void>
  private stopped = false

  constructor(
    private transport: Transport,
    private onRemote: (snapshot: ProgressSnapshot, conflict: boolean) => void,
    private onStatus: (status: SyncStatus) => void,
  ) {}

  async refresh() {
    await this.flush()
    if (this.pending || this.stopped) return
    try {
      const revision = this.canonical?.progress.durChapterTime
      const snapshot = await this.transport.read()
      if (this.stopped || this.pending) return
      if (revision !== this.canonical?.progress.durChapterTime) return
      const changed = !this.canonical || !samePosition(this.canonical.progress, snapshot.progress)
      this.canonical = snapshot
      if (changed) this.onRemote(snapshot, false)
      this.onStatus('saved')
    } catch {
      if (!this.stopped) this.onStatus('offline')
      throw new Error('Không tải được tiến trình từ điện thoại')
    }
  }

  update(position: Position) {
    if (this.stopped || !this.canonical) return
    if (samePosition(this.pending ?? this.canonical.progress, position)) return
    this.pending = { ...position }
    this.onStatus('pending')
  }

  flush(keepalive = false): Promise<void> {
    if (this.running) return this.running
    if (this.stopped || !this.pending || !this.canonical) return Promise.resolve()
    this.running = this.drain(keepalive).finally(() => { this.running = undefined })
    return this.running
  }

  private async drain(keepalive: boolean) {
    while (this.pending && this.canonical && !this.stopped) {
      const position = this.pending
      this.onStatus('saving')
      try {
        const result = await this.transport.write({
          ...position, baseRevision: this.canonical.progress.durChapterTime,
        }, keepalive)
        if (this.stopped) return
        this.canonical = result.snapshot
        if (!result.accepted) {
          this.pending = undefined
          this.onRemote(result.snapshot, true)
        } else if (this.pending === position) {
          this.pending = undefined
        }
        this.onStatus(this.pending ? 'pending' : 'saved')
      } catch {
        if (!this.stopped) this.onStatus('offline')
        return // Retry with the original revision after reconnection.
      }
    }
  }

  stop() { this.stopped = true }
  get hasPending() { return !!this.pending }
}
