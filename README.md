## Introduction
Mole is a static analysis tool that aims to find precise sets of reachable paths to a crash point. 

## Environment Setting
To set Mole, you need to install Java version 1.8. You can get Java version 1.8 with [sdkman](https://sdkman.io/).
You need to download the Android platforms and locate them in a directory. It is available at [android-platforms](https://github.com/Sable/android-platforms). 

## Attribute-sensitive Reachability Analysis (ASRA)
To perform ASRA, you can use the file [mole.jar](). To start the analysis, a config file is essential for the setup of our analysis. In this file,
- `android_platform` is the path to Android platforms jar files. 
- `android_callback_list` is the path of the file, including the set of callback functions in the Android framework (e.g., the file AndroidCallbacks.txt in FlowDroid).
- `output_path` is the output folder.
- `target_app` is the name of the target application to analyze. This file should be added under a folder named ./demo/IntervalAnalysis.  
- `target_package` is the package of the application
- `target_class` is the name of the class where the last function in the application calls stack in the crash stack trace.
- `target_method` is the function signature where the last function in the application calls stack in the crash stack trace.
- `target_line` is the line number of the crash point available in the crash stack trace.

An example of a config file for the crash with issue ID 4707 in AnkiDroid app is shown below:
````
{
	"android_setting": {
    "android_platform": "/home/maryam/Documents/Tool/Android-Testing-Tools/android-platforms/",
    "android_callback_list": "/home/maryam/IdeaProjects/SootTutorial/files/AndroidCallbacks.txt",
    "callback_typestates": "/home/maryam/IdeaProjects/SootTutorial/files/androidCallbackTypestate.xml"
 	 },
    "application_setting": { 
      "target_app": "AnkiDroid-2.9alpha4-4707.apk",
      "target_package": "com.ichi2",
      "target_class": "com.ichi2.anki.AnkiActivity",
      "target_method": "<com.ichi2.anki.AnkiActivity: void startActivityForResult(android.content.Intent,int)>",
      "target_line": "175"
    },
    "analysis_setting": {
      "output_path": "/home/maryam/IdeaProjects/SootTutorial/output/revision/"
    }
  }

````
The example crash stack trace is given below:
````
 FATAL EXCEPTION: main
 Process: com.ichi2.anki, PID: 7186
 android.os.FileUriExposedException: file:///storage/emulated/0/Pictures/img_202006211308531497856152.jpg exposed beyond app through ClipData.Item.getUri()
 	at android.os.StrictMode.onFileUriExposed(StrictMode.java:1799)
 	at android.net.Uri.checkFileUriExposed(Uri.java:2346)
 	at android.content.ClipData.prepareToLeaveProcess(ClipData.java:845)
 	at android.content.Intent.prepareToLeaveProcess(Intent.java:8941)
 	at android.content.Intent.prepareToLeaveProcess(Intent.java:8926)
 	at android.app.Instrumentation.execStartActivity(Instrumentation.java:1517)
 	at android.app.Activity.startActivityForResult(Activity.java:4225)
 	at android.support.v4.app.BaseFragmentActivityJB.startActivityForResult(BaseFragmentActivityJB.java:50)
 	at android.support.v4.app.FragmentActivity.startActivityForResult(FragmentActivity.java:79)
 	at android.app.Activity.startActivityForResult(Activity.java:4183)
 	at android.support.v4.app.FragmentActivity.startActivityForResult(FragmentActivity.java:859)
 	at com.ichi2.anki.AnkiActivity.startActivityForResult(AnkiActivity.java:175)
 	at com.ichi2.anki.multimediacard.fields.BasicImageFieldController$2.onClick(BasicImageFieldController.java:125)
 	at android.view.View.performClick(View.java:5637)
 	at android.view.View$PerformClick.run(View.java:22429)
 	at android.os.Handler.handleCallback(Handler.java:751)
 	at android.os.Handler.dispatchMessage(Handler.java:95)
 	at android.os.Looper.loop(Looper.java:154)
 	at android.app.ActivityThread.main(ActivityThread.java:6119)
 	at java.lang.reflect.Method.invoke(Native Method)
 	at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:886)
 	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:776)
````
When the config file is set, one can use `java` to run the `mole.jar` to start the analysis given the config file and by setting the type of the analysis and callgraph construction algorithm as shown below:
````
java -jar mole.jar -f <config-file> -ca <callgraph algorithm> -type <baseline/reach/event> -t <callgraph construction timeout>
````
Here,
- `-f`: add the path where the config file is located.
- `-ca`: add the desired callgraph construction algorithm in FlowDroid (CHA/SPARK/etc.). The default callgraph construction algorithm is SPARK.
- `-type`: uses the type of analysis that can be any of the three:
  - logging callbacks (baseline),
  - performing reachability analysis (reach),
  - performing attribute-sensitive reachability analysis (event)
- `-t`: add the timeout for callgraph construction performed by flowdroid.

Consider that the `jar` file should be located in a folder where config and output folders reside in it. When the analysis is finished, the instrumented `apk` file is saved under the folder `./output/instrument/`.

## Fuzzing Tool Setup
The fuzzing tools are all available in a docker file available at this [link](). This file is extended from the docker provided by Themis and includes the fuzzing tools Monkey, Ape, Stoat, and FastBot2. In this docker, we add all the fuzzing tools under the path `/home/Themis`. To run each fuzzing tool, one can use the themis.py to start the analysis. For more information about how to use this script, you can check [Themis](https://github.com/the-themis-benchmarks/home).

## Output Result
The output of each fuzzing tool is different, but we log all the exceptions and information about the type of the event (necessary/irrelevant) in a file called "logcat.log". In this file, each line indicates the time, start or end of the callback called, class, and callback name. Below is a sample and part of it: 

````
01-05 23:53:24.123  4858  4858 I <FUZZING>: start necessary: access$100 com.ichi2.anki.NavigationDrawerActivity232911772542
01-05 23:53:24.123  4858  4858 I <FUZZING>: end necessary: access$100 com.ichi2.anki.NavigationDrawerActivity232911781812
01-05 23:53:25.032  4858  4858 I <FUZZING>: start necessary: onNavigationItemSelected com.ichi2.anki.NavigationDrawerActivity233821038427
01-05 23:53:25.032  4858  4858 I <FUZZING>: end necessary: onNavigationItemSelected com.ichi2.anki.NavigationDrawerActivity233821139310
01-05 23:53:25.649  4858  4858 I <FUZZING>: start necessary: initNavigationDrawer com.ichi2.anki.NavigationDrawerActivity234437846617
01-05 23:53:25.649  4858  4858 I <FUZZING>: end necessary: initNavigationDrawer com.ichi2.anki.NavigationDrawerActivity234437922208
01-05 23:53:25.748  4858  4858 E ACRA    : ACRA caught a VerifyError for com.ichi2.anki
01-05 23:53:25.748  4858  4858 E ACRA    : java.lang.VerifyError: Verifier rejected class com.ichi2.anki.Statistics$SectionsPagerAdapter: void com.ichi2.anki.Statistics$SectionsPagerAdapter.<init>(com.ichi2.anki.Statistics, android.support.v4.app.FragmentManager) failed to verify: void com.ichi2.anki.Statistics$SectionsPagerAdapter.<init>(com.ichi2.anki.Statistics, android.support.v4.app.FragmentManager): [0x3B] Constructor returning without calling superclass constructor (declaration of 'com.ichi2.anki.Statistics$SectionsPagerAdapter' appears in /data/app/com.ichi2.anki-1/base.apk)
01-05 23:53:25.748  4858  4858 E ACRA    : 	at com.ichi2.anki.Statistics.onCollectionLoaded(Statistics.java)
01-05 23:53:25.748  4858  4858 E ACRA    : 	at com.ichi2.anki.AnkiActivity.onLoadFinished(AnkiActivity.java)
01-05 23:53:25.748  4858  4858 E ACRA    : 	at com.ichi2.anki.AnkiActivity.onLoadFinished(AnkiActivity.java)
01-05 23:53:25.748  4858  4858 E ACRA    : 	at android.support.v4.app.LoaderManagerImpl$LoaderInfo.callOnLoadFinished(LoaderManager.java)
01-05 23:53:25.748  4858  4858 E ACRA    : 	at android.support.v4.app.LoaderManagerImpl$LoaderInfo.onLoadComplete(LoaderManager.java)
01-05 23:53:25.748  4858  4858 E ACRA    : 	at android.support.v4.content.Loader.deliverResult(Loader.java)
01-05 23:53:25.748  4858  4858 E ACRA    : 	at com.ichi2.async.CollectionLoader.deliverResult(CollectionLoader.java)
01-05 23:53:25.748  4858  4858 E ACRA    : 	at com.ichi2.async.CollectionLoader.deliverResult(CollectionLoader.java)
01-05 23:53:25.748  4858  4858 E ACRA    : 	at android.support.v4.content.AsyncTaskLoader.dispatchOnLoadComplete(AsyncTaskLoader.java)
01-05 23:53:25.748  4858  4858 E ACRA    : 	at android.support.v4.content.AsyncTaskLoader$LoadTask.onPostExecute(AsyncTaskLoader.java)
01-05 23:53:25.748  4858  4858 E ACRA    : 	at android.support.v4.content.ModernAsyncTask.finish(ModernAsyncTask.java)
01-05 23:53:25.748  4858  4858 E ACRA    : 	at android.support.v4.content.ModernAsyncTask$InternalHandler.handleMessage(ModernAsyncTask.java)
01-05 23:53:25.748  4858  4858 E ACRA    : 	at android.os.Handler.dispatchMessage(Handler.java:102)
01-05 23:53:25.748  4858  4858 E ACRA    : 	at android.os.Looper.loop(Looper.java:154)
01-05 23:53:25.748  4858  4858 E ACRA    : 	at android.app.ActivityThread.main(ActivityThread.java:6119)
01-05 23:53:25.748  4858  4858 E ACRA    : 	at java.lang.reflect.Method.invoke(Native Method)
````

Once the fuzzing is finished, one can search for the existence of a crash in the collected files with the script `scripts/check.sh`. This script accepts the directory path where the folders of fuzzing outputs are stored. 
