crayfis_android
===============

Code for the android implementation of the Crayfis app.
We  also keep reference information e.g. about protocol, data format, etc on the wiki found in this repository.

Getting Started
---------------

### Setting up an Eclipse Project
First, download and install the [ADT bundle](http://developer.android.com/sdk/installing/bundle.html).
When you start Eclipse for the first time, you'll be asked to create a "Workspace".
Choose a location; it doesn't really matter where.

Next, clone this git repository to a convenient location (other than the workspace).

Finally, from Eclipse select `File > New... > Other...` and select "Android Project from Existing Code".
Point the "Root directory" field to wherever you checked out this git repository, and make sure that "Copy projects into workspace" is *not* checked.

### Setting up an android emulator
... todo ... (or just follow instructions from Google)

### Running on an android phone
Find the settings panel, and open up "Developer options".
Make sure that USB debugging is checked.
Now whenever the phone is connected via USB, you should be able to select the device when you hit "Run" in Eclipse.
