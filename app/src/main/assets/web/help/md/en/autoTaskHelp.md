# Scheduled tasks

Scheduled tasks use five-field Cron to run synchronous JS. The environment reuses source, sign-in, cookie and cache capabilities. The final return value is parsed into actions such as contents updates or notifications.

## Cron
Fields are minute, hour, day of month, month and day of week. Use `*`, lists, ranges and steps according to the app syntax. Choose a reasonable schedule; battery/network/system conditions can affect actual execution time.

## Results
Preserve result types and protocol keys shown in the examples. Book-update actions need valid book identities; notifications need the appropriate content. Verify a manual run and logs before enabling schedules.
Avoid continuous network tasks and secrets in notifications.

## Examples and API signatures

Method names, keys, sample data and syntax are preserved from the source reference.

```json
[
  { "type": "refreshToc", "bookUrl": "https://..." },
  { "type": "notify", "title": "完成", "content": "全部更新完毕" }
]
```


```json
{ "type": "notify", "title": "签到", "content": "已签到 {time}" }
```


```json
{
  "actions": [
    { "type": "refreshToc", "bookUrl": "https://..." }
  ]
}
```


```json
{
  "type": "refreshToc",
  "bookUrl": "https://example.com/book/123",
  "notify": {
    "enable": true,
    "minCount": 1,
    "title": "《{book}》更新了",
    "content": "新增 {newCount} 章\n最新：{chapter}\n时间：{time}"
  },
  "cache": {
    "enable": true
  }
}
```


```json
{
  "type": "notify",
  "title": "每日签到",
  "content": "已完成\n时间：{time}",
  "level": "high",
  "id": 1
}
```


