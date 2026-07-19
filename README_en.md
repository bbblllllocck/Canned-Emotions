<div align="center">

<h1>Canned-Emotions - 听点什么</h1>

> 以 Gemini Embedding 2 为核心的 Android 本地音乐智能播放工具。<br>
> A smart local music playing tool powered by Gemini Embedding 2.
 
**This README is translated by Gemini**

<p>
  <a href="README.md">简体中文</a> &nbsp;|&nbsp; 
  <a href="README_en.md"><b>English</b></a>
</p>

<br>

<!-- 这是一个利用 img 伪装成的大体积下载按钮 -->
<a href="https://github.com/bbblllllocck/Canned-Emotions/releases">
  <img src="https://img.shields.io/badge/Download_Latest-Android_APK-000000?style=for-the-badge&logo=android&logoColor=white" alt="Download Release"/>
</a>

</div>

<br>

Do you also, like me, have a massive, incredibly diverse local music library, yet sometimes can't find a song you want to hear?  
Or does your local player's random algorithm shuffle from a rock song one second to a gentle classical piece the next?

This software can, to some extent, improve your playback experience—**making it smoother, more fitting for the moment, and more to your liking.**

________________________________________________________________________________________

## What can it do?

Don't know what to listen to today? Just pick a random song to start, sounds pretty good.

Start roaming from here. The genre stays coherent, and the vibe gets better as you listen.

Want to pick a BGM for your video or your current environment? Tap the search box, and the music comes right up!

There are also brand new algorithm templates. Want more randomness? Want to hear something you haven't heard before? Or perhaps interweave multiple albums or artists? Or just want more instrumental music? No problem! The customizable, diverse algorithms can make your experience much more personalized.

Getting a bit tired of a song? Hit skip, and the punishment vector mechanism will reduce similar recommendations in the future. Super convenient!

_________________________________________________________________________________________

## Quick Start
### Prerequisites
1. Download the latest [Release](https://github.com/bbblllllocck/Canned-Emotions/releases) and install it on your Android device.
2. In the scan page, add the directories you want to scan.
3. In the API page, add one or more API keys obtained from [Google AI Studio](https://aistudio.google.com/).
(If you add multiple APIs, it can automatically rotate to one with available quota)
(Free tier APIs are recommended)

4. In the database page, start the audio processing task (This process may require a network proxy).

### Start Listening
1. Go back to the start page, pull up the drawer from the bottom, long-press the random button or tap the selected button to pick a starting song, then long-press the start button to begin roaming playback.
2. You can also tap the album display area in the center of the screen in symmetrical retrieval mode, input any text that describes a song, tap the search button, and start listening to music similar to your description (This process may require a network proxy).


> To learn more about the playback queue logic and algorithm module, please refer to [Algorithms and Playlists](docs/algorithmAndPlaylist_en.md)
>
> Smarter assisted retrieval, more algorithms, and integration with other players are on the roadmap! (Just drawing a pie / making promises)

__________________________________________________________________________________________

## How does it work?

The principle is very simple. We just downgrade the audio quality, cut it into 180-second snippets, throw the whole thing to Gemini Embedding 2 to generate vectors, and then store them in the [ObjectBox](https://github.com/objectbox) database. When you want something, you just use text or whatever to generate a vector, search with it, or directly use existing vectors to find similarities, add some small algorithms to correct the results, and that's it. It's that simple.


__________________________________________________________________________________________

## Who is this for?
If you have a massive and diverse local music library and want a smart playback experience on Android, this software might give you some new ideas.

It helps you more easily retrieve music you can describe but don't know the exact name of, or builds a more coherent and diverse playback experience based on how things actually sound.

If you want a more traditional local music playback experience, I sincerely recommend [Salt Player](https://github.com/Moriafly/SaltPlayerSource), the best local music player in the world.

--------------------------------------------------------------------------------

## Learn about the design ideas
[Watch the explainer video with pretty bad view counts](https://www.bilibili.com/video/BV1o3E16TEHN/?share_source=copy_web&vd_source=e314e0085f08a8dc001414e7d1fd2c6b)

__________________________________________________________________________________________

## Disclaimer

> [!WARNING]
> **API Security**
> 
> The API storage within the software is encrypted and only communicates with Google servers, but the author does not fully guarantee its security. The software also lacks intelligent quota management. Please keep your API keys safe and manage your quota plans yourself to prevent financial loss.

> [!CAUTION]
> **Model Fluctuation Risks**
> 
> The Gemini Embedding 2 model this project relies on is in preview phase, which means it could go offline or change at any time. Please do not use it in a production environment, and do not expect the software to function normally forever. Please also comply with Google's Terms of Service and usage policies when using it.

The author of this software lacks all basic knowledge regarding security, music taste, Android development, programming, system design, and the open-source community. Anyway, this is a rapid prototype, or rather a toy, whatever. I do not guarantee code quality or software stability, nor do I guarantee completeness of functionality and logic.


## Keywords for assisted search: 

Music MusicRecommendation GeminiEmbedding2 LocalMusic MusicPlayback LocalMusicPlayback Embedding AudioRetrieval MusicLibrary
