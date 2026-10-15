# Quy tắc đọc trực tuyến

Quy tắc TTS trực tuyến là quy tắc URL giống nguồn. Cấu hình phương thức yêu cầu, header, body và cách lấy dữ liệu âm thanh theo dịch vụ đang dùng.

Các tham số JS cung cấp văn bản, giọng và tốc độ theo hợp đồng ở ví dụ; danh sách phát ngôn viên và cảm xúc cũng phải giữ đúng kiểu. Không đổi mã giọng/ID kỹ thuật khi dịch tên hiển thị.
Thử một câu ngắn có dấu tiếng Việt và tiếng Trung, sau đó thử ngắt câu và đoạn dài. Chỉ gửi nội dung cho dịch vụ bạn đã cấu hình; token nằm trong cấu hình, không trong tên giọng hoặc log chia sẻ.

## Ví dụ và chữ ký API

Tên hàm, khóa, dữ liệu mẫu và cú pháp được giữ nguyên từ tài liệu nguồn.

```
speakText //朗读文本
speakSpeed //朗读速度,5-50
currentToneID //当前分角色朗读选中的发言人 toneID,没有配置时为空
currentSpeakerName //当前分角色朗读选中的发言人展示名
currentEmotionName //当前分角色朗读选中的情绪名
currentEmotionTag //当前分角色朗读选中的情绪标志,例如[高兴]
currentSpeechRouteJson //当前配音路由完整JSON
```

```
http://tts.baidu.com/text2audio,{
    "method": "POST",
    "body": "tex={{java.encodeURI(java.encodeURI(speakText))}}&spd={{String((speakSpeed + 5) / 10 + 4)}}&per=5003&cuid=baidu_speech_demo&idx=1&cod=2&lan=zh&ctp=1&pdt=1&vol=5&pit=5&_res_tag_=audio"
}
```


```json
[
  {
    "speakerName": "晓晓",
    "toneID": "zh-CN-XiaoxiaoNeural"
  },
  {
    "speakerName": "云希",
    "toneID": "zh-CN-YunxiNeural"
  }
]
```


```json
[
  {
    "groupId": "female",
    "groupName": "女声",
    "items": [
      {
        "speakerName": "晓晓",
        "toneID": "zh-CN-XiaoxiaoNeural"
      }
    ]
  },
  {
    "groupId": "male",
    "groupName": "男声",
    "items": [
      {
        "speakerName": "云希",
        "toneID": "zh-CN-YunxiNeural"
      }
    ]
  }
]
```


```json
{
  "method": "POST",
  "body": {
    "text": "{{speakText}}",
    "voice": "{{currentToneID || 'zh-CN-XiaoxiaoNeural'}}"
  }
}
```


```json
[
  {
    "emotionName": "高兴",
    "emotionTag": "[高兴]"
  },
  {
    "emotionName": "悲伤",
    "emotionTag": "[悲伤]"
  }
]
```


```json
{
  "method": "POST",
  "body": {
    "text": "{{currentEmotionTag || ''}}{{speakText}}",
    "voice": "{{currentToneID}}"
  }
}
```


