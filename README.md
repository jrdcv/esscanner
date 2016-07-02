
ESScanner

An external storage scanner to examine external storage
and show the largest files, most comment file extensions
and an average file size.

Comments:

This is the first pass. It uses text items to show scanned
values rather than lists, which seemed reasonable but led
to some complications. 

The UI is very basic so far. Using the TextViews to show
scan results can lead to some visually unpleasant wrapping
when numbers get large. Also file sizes should probably be
shown in a more readable way than purely by byte count.

Tested on LG Nexus 4 (Android 5.1.1)
          Samsung S6 (Android 6.0.1)
          Toshiba AT100 tablet (Android 4.0.4)

So far unsuccessful on the Toshiba; we don't seem to be
able to access external storage. Time has limited the
investigation, still in progress.
