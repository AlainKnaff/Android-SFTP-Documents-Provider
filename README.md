# Android-SFTP-Document-Provider
This app makes the android default file manager an SFTP client

## Status

- This package is a clone of [Android-SFTP-Documents-Provider](https://github.com/cheng6563/Android-SFTP-Documents-Provider)

- It fixes a number a number of bugs in the original (unsynchronized multi-thread access to ssh backend, no handling of closed ssh handles, ...), and incompatibilities with current compilation environments

- Another slightly more recent and more fully featured original package exists: [FileManagerUtils](https://github.com/rikyiso01/FileManagerUtils): however this was incompatible with current compilation environments too and had a number of bugs too, which were harder to fix. So I went with the earlier version

## Plans

- Move account configuration to application's own settings

- Add possibility to use a private/public ssh key pair

- Add possibility to specify (or autodetect) home directory of sftp user on server.

## Installation

Once this is released, download and install the latest app from the [release section](https://github.com/AlainKnaff/Android-SFTP-Documents-Provider/releases)

However, for now you need to compile it yourself:

	./gradlew build -x :app:lintAnalyzeDebug -x :app:lintDebug 

## Usage

- Open the global settings, go to the account section and add a new SSH File Transfer Protocol account, enter your credentials

- Open the android default file manager and on the left panel you should see the sftp section where you can connect to the sftp server.

- For some reason, on newer Android version, this doesn't work on the "Files by Google" filemanager (yet). However, it does work in the older com.google.android.documentsui file manager. Use the Activity Manager app from F-Droid to make a shortcut to launch com.google.android.documentsui

- It does work from the Open file dialog of most applications (as long as they use documents' UI, which most applications nowadays do)
