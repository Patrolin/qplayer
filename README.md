# qplayer
A simple Android music player

## features
Qplayer
- Songs
  - play in order
  - search name + artist // TODO
  - sort by name/artist/time // TODO
  - show songs belonging to multiple playlists? // TODO
  - \> More // TODO
    - Info (Name, Artist, Playlists) // TODO
    - Delete w/ confirm // TODO
  - controls
    - prev/next/pause // TODO
    - toggle shuffle // TODO
- Playlists
  - Add (yt) // TODO
    - Download
    - \> Shuffle // TODO
    - \> Delete w/ confirm (playlist/videos) // TODO
  - filter by multiple playlists // TODO
  - Settings // TODO
    - rate limit // TODO
    - audio ducking // TODO

## dev
Pairing a physical device:
```
PC
  Connect phone by USB cable
  Click "Pair using Wi-Fi"
Phone
  Open developer options
  Enable "USB debugging" and "Wireless debugging"
  Click on "Wireless debugging" > Pair device with QR code
  Scan QR code
PC
  Disconnect USB cable
```

Adding icon:
- Right click on res/drawable > New -> Vector asset > Clip art

Jetpack Compose state is retarded, you must use exactly the following:
```kt
val (nonce, setNonce) = rememberSaveable { mutableStateOf(0, neverEqualPolicy()) }
fun incrementNonce() { setNonce(nonce + 1) }
```

ID3:
- https://web.archive.org/web/20120310015458/http://www.fortunecity.com/underworld/sonic/3/id3tag.html
- https://id3.org/Developer%20Information