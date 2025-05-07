# Random Image Sharer

## Overview
Random Image Sharer is an Android application that allows users to randomly select an image from a specified folder, preview it, and share it via Twitter. After successful sharing, the application automatically deletes the image file to maintain a clean storage environment.

## Features
- Randomly selects an image from a designated folder.
- Displays a preview of the selected image.
- Shares the image directly to Twitter.
- Automatically deletes the image file after sharing.

## Project Structure
```
RandomImageSharer
├── app
│   ├── src
│   │   ├── main
│   │   │   ├── java
│   │   │   │   └── com
│   │   │   │       └── randomimagesharer
│   │   │   │           ├── MainActivity.kt
│   │   │   │           ├── ImagePreviewActivity.kt
│   │   │   │           └── utils
│   │   │   │               └── FileUtils.kt
│   │   │   ├── res
│   │   │   │   ├── layout
│   │   │   │   │   ├── activity_main.xml
│   │   │   │   │   └── activity_image_preview.xml
│   │   │   │   ├── drawable
│   │   │   │   └── values
│   │   │   │       ├── colors.xml
│   │   │   │       ├── strings.xml
│   │   │   │       └── styles.xml
│   │   │   └── AndroidManifest.xml
│   │   └── test
│   │       └── java
│   │           └── com
│   │               └── randomimagesharer
│   │                   └── ExampleUnitTest.kt
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
```

## Setup Instructions
1. Clone the repository to your local machine.
2. Open the project in Android Studio.
3. Ensure you have the necessary permissions set in the `AndroidManifest.xml` for accessing external storage.
4. Build and run the application on an Android device or emulator.

## Usage
- Launch the application.
- Tap the button to select a random image from the specified folder.
- Preview the selected image.
- Tap the share button to share the image on Twitter.
- The image will be deleted from the storage after successful sharing.

## License
This project is licensed under the MIT License. See the LICENSE file for more details.