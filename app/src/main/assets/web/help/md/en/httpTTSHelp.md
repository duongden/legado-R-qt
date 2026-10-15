# HTTP TTS rules

Online TTS uses a URL rule similar to book sources. Configure method, headers, body and audio extraction for your chosen service.

JS parameters provide text, voice and speed according to the example contract; speaker/emotion lists must preserve the required types. Translate display names without changing technical voice codes/IDs.
Test a short accented Vietnamese/Chinese sentence, then punctuation and longer paragraphs. Send text only to the service you configure; keep tokens in configuration, not voice names or shared logs.

## Examples and API signatures

Method names, keys, sample data and syntax are preserved from the source reference.

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


