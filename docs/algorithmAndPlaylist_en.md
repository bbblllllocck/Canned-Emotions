In the 0.4-alpha update, a massive update has been released!
# The Playlist Slider
Then there's the playback queue. I introduced a slider. The interval between the slider and the currently playing song is the Cloud Zone (a buffer), which displays the music that will play next. If you slide it down, this area gets longer.

Songs in the buffer zone will not be penalized with a skip downgrade when you execute a skip operation. The original intention of this feature is to solve the sense of loss you feel when a list you're looking at gets refreshed.

Friends who have used social media should remember: switching back to Bilibili from the background, seeing something interesting—only for it to immediately auto-refresh away. This feature is to avoid that tragedy.

# Algorithms
Using vector similarity as the baseline weight, the algorithm template can meticulously add or deduct weights for each song to avoid the playlist becoming too rigid.

**Irrelevance Coefficient** 

This is the baseline value for raw weight penalties. For example, if this coefficient is 0.2, and after album deduplication calculation, a subsequent song needs to be given a 0.5 penalty coefficient, then the impact on the raw weight is 0.2 * 0.5 = 0.1.

It is not recommended to set this value too high, because the similarity of basically all music falls in the 0.7-0.9 range. Since the algorithm page UI isn't very well done right now, I recommend tweaking all parameters on top of the original default template.

**Random Temperature**

Applies a softmax pick to the songs after all weight calculations are done, slightly shuffling the queue. The higher the temperature, the higher the randomness.

> I originally wanted to just change the UI styling. But the model in Antigravity suddenly got really stupid, the genius programmer has fallen!
> 
> Wait until I refactor the UI next time! Anyway, probably not many people will actually use this this time, if you don't understand it just ask me directly.
> 
> Anyway, all the detailed settings basically mean: after playing for a few times or a few minutes, the raw weight gets subtracted by an irrelevance coefficient. That's the gist of it.
