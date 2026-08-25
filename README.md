# AnimatedQRCodeReader

**Update:** There is a new cross-platform Ionic version which supports two-way communication: <https://github.com/tony-xlh/QRTransfer/>

Animated QR code reader. The repo contains the generator as well.

Using the generator and reader, it is possible to transfer data between two devices without an Internet connection.

The generator and reader are implemented in JavaScript using the [QR code generator](https://github.com/kazuhikoarase/qrcode-generator/) and the Dynamsoft Barcode Reader [bundle](https://www.dynamsoft.com/barcode-reader/overview/).

An Android project using the [Dynamsoft Barcode Reader bundle](https://www.dynamsoft.com/barcode-reader/overview/) is included as well.

![](https://github.com/xulihang/AnimatedQRCodeReader/releases/download/builds/video.gif)

PS: Dynamsoft Barcode Reader is a commercial SDK. You need to apply for a license to use it: [apply for a trial](https://www.dynamsoft.com/customer/license/trialLicense/?product=dbr).

## How it works

The generator splits a file into chunks ("index/total|data"), and renders each chunk as a QR code image in sequence. The scanner keeps reading QR codes and reassembles the chunks;

```text
1/7|payload_image.png|image/png|<chunk 1 bytes>
2/7|<chunk 2 bytes>
...
7/7|<chunk 7 bytes>
```

The first frame carries the filename and MIME type. The scanner stops automatically once all frames have been received. Missing frames are reported and can be re-scanned.

## Online demo

[Generator](https://tony-xlh.github.io/AnimatedQRCodeReader/generator/generator.html)

[Scanner](https://tony-xlh.github.io/AnimatedQRCodeReader/reader/scanner.html)

## Web scanner

`reader/scanner.html` uses the latest [dynamsoft-barcode-reader-bundle](https://www.npmjs.com/package/dynamsoft-barcode-reader-bundle) (Dynamsoft Barcode Reader for JavaScript v11, bundled from jsdelivr) with the `CaptureVisionRouter` API. It can read QR codes from the live camera or from image files, and stops the camera when the transfer completes.

To use it, open `generator/generator.html` on one device, pick a file and generate the animated QR codes, then open `reader/scanner.html` on another device with a camera and press "Start Scanning".

## Android reader

The Android project is in `reader/dcesample` and ships with the Dynamsoft Barcode Reader bundle (`com.dynamsoft:barcodereaderbundle:11.6.2000` from the Dynamsoft Maven repo).

Build it with:

```bash
cd reader/dcesample
./gradlew assembleDebug
```

The app has three screens:

- **Home**: plays the embedded animated QR video (a small file transferred as `payload_image.png`, stored as 7 frames). Press "Simulate Receive" to decode the frames locally and reassemble the file without a camera. "Open Camera" starts the live scanning mode.
- **Camera**: live scanning with Dynamsoft Camera Enhancer; each decoded frame is appended to the transfer, and the received file is shown in the result screen when all frames arrive.
- **Result**: previews the reassembled file and offers a Save button.

## Blog

<https://www.dynamsoft.com/codepool/transfer-data-with-animated-qr-codes.html>

## What Advantages of Dynamsoft Barcode Reader Does this Demo Showcase?

1. The ability to read high-version QR codes.
2. The speed of reading barcodes from camera frames.
3. The great customizability.

## References

[txqr](https://github.com/divan/txqr/)
