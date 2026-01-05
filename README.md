# Time of Flight Camera Example 
<p><img src="docs/demo.gif"/></p>

This is an example app that demonstrates how to capture and process data from a Time of Flight camera, specifically the front-facing "3D Camera" on the Samsung S10 5G.

## How to Use
Clone and run on an Samsung S10 5G device from Android studio (3.5.1 at the time of publishing).

There is also an associated post [here](https://medium.com/@lukesma/working-with-the-3d-camera-on-the-samsung-s10-5g-4782336783c).

## Samsung A80

This is update to support also the Samsung A80 device with IMX316 camera


## Custom signing the APK

```
nix-shell -p openjdk

keytool -genkeypair -v -keystore release.keystore -alias your-key-alias -keyalg RSA -keysize 2048 -validity 10000

base64 release.keystore > release.keystore.b64
```
