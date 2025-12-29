# Android-SFTP-Document-Provider

This app makes the android default file manager an SFTP client via SAF
(Storage Access Franework).

## Status

- This package is a clone of [Android-SFTP-Documents-Provider](https://github.com/cheng6563/Android-SFTP-Documents-Provider)

- It fixes a number a number of bugs in the original (unsynchronized multi-thread access to ssh backend, no handling of closed ssh handles, ...), and incompatibilities with current compilation environments, and a number of features (see below)

- Another slightly more recent and more fully featured original package exists: [FileManagerUtils](https://github.com/rikyiso01/FileManagerUtils): however this was incompatible with current compilation environments too and had a number of bugs too, which were harder to fix. So I went with the earlier version

## Features

- Account configuration is done using the application's own database,
  which allows more flexibility than Android's built-in account
  management (such as, editing existing accounts)

- Added possibility to use a private/public ssh key pair

- Added possibility to specify home directory of sftp user on server.

## Installation

Download and install the latest app from the [release section](https://github.com/AlainKnaff/Android-SFTP-Documents-Provider/releases)

However, you may also compile it yourself:

	./gradlew build

## Usage

- On the applications main activity (main page), add a new SSH account
  (file system root), using the green plus button at the bottom right

- Optionally generate an SSH key pair, and share the public key, to
  place into the <code>~/.ssh/autorized_keys</code> file on the SFTP server.

- Open the android default file manager and on the left panel you
  should now see the sftp section where you can connect to the sftp
  server.

- For some reason, on newer Android version, this doesn't work on the
  "Files by Google" filemanager (yet). However, it does work in the
  older com.google.android.documentsui file manager, which you can
  access from the "Browse files" button in this application's main
  activity. You may also use the Activity Manager app from F-Droid to
  make a shortcut to launch com.google.android.documentsui

- This document provider does work from the Open file dialog of most
  applications (as long as they use documents' UI, which most
  applications nowadays do)
