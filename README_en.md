# Canned-Emotions - 听点什么
> 以 Gemini Embedding 2 为核心的 Android 本地音乐智能播放工具。
> A smart local music playing tool powered by Gemini Embedding 2.

Language: [简体中文](README.md)  [English](docs/README_en.md)


Do you, just like me, own a massive and diverse local music library but sometimes struggle to find the "right" song? Or your player's random shuffle algorithm jumps from hard rock to gentle classical music?

This app would -- partly, improve your playing experience, making it smother, more context-aware, and more matched with your mood.

**Download [GitHub Release](https://github.com/bbblllllocck/Canned-Emotions/releases)**

I spent nearly two weeks to build this prototype after seeing the release of Gemini Embedding 2, to get a smarter music playing ability by using multimodal AI to generate vectors for your audio files.

And based on my (limited) understanding of AI and music playback, I’ve designed two primary ways to interact with your library:

________________________________________________________________________________________


## Examples

### Better Random Play
> Random Shuffle (maybe not that random) with a specific music as a starting point, to hear the musics with simular style
>
> seriously, this is what really useful.
#### **Example 1**

<img src="assets/img/roaming1.jpg" alt="漫游工具的示例用法" title="漫游1" width="350">



Listen To The Search Results (Snippets):

(Streaming links provided to avoid copyright issues)

#Starting point：[Ans IsL - HOYO-MiX](https://music.163.com/#/song?id=1920737988)

[Inverted Island - Obfusc](https://music.163.com/#/song?id=28855562)  
[Oceanic Glow - Obfusc](https://music.163.com/#/song?id=28855563)  
[STRANGE SUITCASE - 日向大介](https://music.163.com/#/song?id=480675)  
[不安之种 - 三Z-STUDIO/HOYO-MiX](https://music.163.com/#/song?id=2658039441)  
[妖刀幻境-三Z-STUDIO/HOYO-MiX](https://music.163.com/#/song?id=2658039040)  
[!BUG!-三Z STUDIO/HOYO-MiX](https://music.163.com/#/song?id=2658039017)  
[Door - C418](https://music.163.com/#/song?id=4010184)  
[Blind Spots - C418](https://music.163.com/#/song?id=27961150)  
[Mall - C418](https://music.163.com/#/song?id=27961173)
[Moog City 2 - C418](https://music.163.com/#/song?id=27961152)



[Learn the Example Music Library](docs/musicLib.md)

#### **Example 2**


<img src="assets/img/roaming2.jpg" alt="漫游工具的示例用法" title="漫游2" width="350">


Listen To The Search Results (Snippets):

(Streaming links provided to avoid copyright issues)

#起始音乐：[NO, Thank You! - 放課後ティータイム](https://music.163.com/#/song?id=1317233324)

[Addicted... - 藍井エイル](https://music.163.com/#/song?id=27969039)   
[ごめんね、いいコじゃいられない。 - 沢井美空](https://music.163.com/#/song?id=27902540)  
[ambiguous GARNiDELiA](https://music.163.com/#/song?id=1347688545)
[Gravity - GARNiDELiA](https://music.163.com/#/song?id=1347687847)  
[ORiGiNAL-GARNiDELiA](https://music.163.com/#/song?id=1347688546)
[独法師 - TetraCalyx](https://music.163.com/#/song?id=468513224)  
[崩壊世界の歌姫 - TetraCalyx](https://music.163.com/#/song?id=468513218)




[Learn the Example Music Library](docs/musicLib.md)



### Semantic Search The Music That Matches the Vibe

> Describe the current vibe, music type, feelings, to search the suitable music.

#### **Example 1**


Search Query:
Upbeat Pop-Rock, 124 BPM. Full pop guitar strumming with crisp drum beats. Reflecting the bright feeling of sunlight on a desk and a driven, productive workflow.


<img src="assets/img/search1.jpg" alt="对称检索的示例用法" title="检索1" width="350">

<img src="assets/img/searchresult1.jpg" alt="检索结果" title="检索结果1" width="350">


Listen To The Search Results (Snippets):

(Streaming links provided to avoid copyright issues)

[シリウス(Instrumental) - 藍井エイル](https://music.163.com/#/song?id=27969040)  
[THEREAFTER - 日向大介](https://music.163.com/#/song?id=480654)  
[维多利亚式服务 - 三Z-STUDIO/HOYO-MiX](https://music.163.com/#/song?id=2657833383)   
[灾难土壤 - 三Z-STUDIO/HOYO-MiX](https://music.163.com/#/song?id=2657833393)  
[荒野之风 - 三Z-STUDIO/HOYO-MiX](https://music.163.com/#/song?id=2657830928)  
[真·预言之下 - 三Z-STUDIO/HOYO-MiX](https://music.163.com/#/song?id=2657830947)  
[躁动狂潮 - 三Z-STUDIO/HOYO-MiX](https://music.163.com/#/song?id=2658039024)  
[胜负已分 - 三Z-STUDIO/HOYO-MiX](https://music.163.com/#/song?id=2658039013)




[Learn the Example Music Library](docs/musicLib.md)

#### **Example 2**

Example 2
Search Query: Warm blankets, cozy and peaceful resting environment.

<img src="assets/img/search2.jpg" alt="对称检索的示例用法" title="检索2" width="350">

<img src="assets/img/searchresult2.jpg" alt="检索结果" title="检索结果2" width="350">



Listen To The Search Results (Snippets):

(Streaming links provided to avoid copyright issues)

[The Descent - Stafford Bawler](https://music.163.com/#/song?id=28855558)  
[Haunted - Stafford Bawler](https://music.163.com/#/song?id=426881208)  
[One More Day - Lena Raine/Minecraft](https://music.163.com/#/song?id=1887199302)  
[Chrysopoeia - Lena Raine/Minecraft](https://music.163.com/#/song?id=1454344539)
[Eld Unknown - Lena Raine/Minecraft](https://music.163.com/#/song?id=2145324212)
[komorebi - Minecraft/谷岡久美](https://music.163.com/#/song?id=2145324209)
[pokopoko -Minecraft/谷岡久美](https://music.163.com/#/song?id=2145324210)
[yakusoku - Minecraft/谷岡久美](https://music.163.com/#/song?id=2145324211)




[Learn the Example Music Library](docs/musicLib.md)



_________________________________________________________________________________________



## How to Use



### Add Path & Scan

> #The current scanning algorithm is really f**king slow.


Enter "扫描" page from menu "菜单"

Add your local music library path in the "扫描" interface and click "开始扫描".


### Add Gemini API Key

>> #This app is powered by Gemini API Key. Get it on [Google AI Studio](https://aistudio.google.com/app/apikey) for free. (Make sure your network environment meets Google's requirements).
>
> #Requires a Gemini API Key. The free tier quota is usually enough.

Enter "API" page from menu "菜单"

Click"添加API"，enter your Gemini API Key，click"保存".

### Vector Generation & Database Indexing

> #the transfomer lib I use might blocking the main scope, um why I don't just fix it and avoid saying it in readme?
>
> #This function requires using Gemini API，make sure it's available in your current network environment.
>
> #I wrote multi-API rotation, but it may not work well now. If needed, remove the first 1 or 2 API manually and that may help. 


Open the menu and enter the `数据库` page.

On this page, you can see basic music info in the database. Click `开始`, and the app will slice audio, generate vectors, and save them to the database. Depending on your library size, this may take some time.

### Symmetric Search on Start Page

Open the Start page, then switch the mode button on the left to `对称`.

<img src="assets/img/searchtutorial1.jpg" alt="Search tutorial" title="Search tutorial 1" width="350">

Tap the center album area to open the search box. In the input box, describe the mood, style, and feeling you want to hear. Then tap `Search` to get a recommended playlist.

<img src="assets/img/searchtutorial2.jpg" alt="Search tutorial" title="Search tutorial 2" width="350">

### Select One Song in Playlist Drawer to Start Roaming

Open the Start page, then drag up or tap the drawer handle to open the playlist drawer.

<img src="assets/img/roamingtutorial1.jpg" alt="Roaming tutorial" title="Roaming tutorial 1" width="350">

There are two ways to choose the start song for roaming:

Tap `选定` to open a search box. Enter the song name you want, then tap one result to select it.

<img src="assets/img/roamingtutorial2.jpg" alt="Roaming tutorial" title="Roaming tutorial 2" width="350">

Or tap `随机` to pick one song as the starting point.

<img src="assets/img/roamingtutorial3.jpg" alt="Roaming tutorial" title="Roaming tutorial 3" width="350">

After that, tap `开始`. The selected song will be used as the seed, and the app will generate a playlist with similar style.

<img src="assets/img/roamingtutorial4.jpg" alt="Roaming tutorial" title="Roaming tutorial 4" width="350">

__________________________________________________________________________________________

## How It Works

The idea is simple: lower the audio quality a little, cut songs into 80-second chunks, send them to Gemini Embedding 2 to get vectors, and store vectors in [ObjectBox](https://github.com/objectbox). When you want music, generate a vector from text (or another input), then search for similar vectors.

__________________________________________________________________________________________

## Who Is It For?
If you have a large and diverse local music library, and you want a smarter playback experience on Android, this app may give you a new idea.

It can help you find songs that match your current mood, or keep random play more style-consistent. Maybe. To be honest, I am still not fully sure how practical this direction is.

If you prefer a more traditional local music player experience, I sincerely recommend [Salt Player](https://github.com/Moriafly/SaltPlayerSource), the best local music player in this world.

__________________________________________________________________________________________

## Interested?
If this direction sounds interesting to you, feel free to report bugs, suggest features, or join development. Or just star the repo or download it, so I know there is at least one more person who likes this idea.

__________________________________________________________________________________________

## Disclaimer

API keys in the app are stored in encrypted form and only communicate with Google servers, but I cannot guarantee full security. Please keep your API keys safe and manage your quota plan yourself.

The Gemini Embedding 2 model used by this project is in preview, and it may change or go offline at any time. Do not use this project in production, and do not assume it will always work. Please also follow Google's terms and usage policies.

This software took about two weeks to build (including eating, resting, and messing around). I also lack almost every kind of formal knowledge in security, music taste, Android development, programming, system design, and open-source community practices. In short, this is a fast prototype, or just a toy. No guarantee for code quality, software stability, or complete logic.

## Keywords for Search

music
music recommendation
Gemini Embedding 2
local music
music playback
local music playback
Embedding
audio retrieval
music library
