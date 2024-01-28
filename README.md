## Introduction
Mole is a static analysis tool that aims to find precise sets of reachable paths to a crash point. 

## Environment Setting
To set Mole, you need to install Java version 1.8. You can get Java version 1.8 with [sdkman](https://sdkman.io/).
You need to download the Android platforms and locate them in a directory. It is available at [android-platforms](https://github.com/Sable/android-platforms). 

## Attribute-sensitive Reachability Analysis (ASRA)
To perform ASRA, you can use the file [mole.jar](). To start the analysis, a config file is essential to define the locations where 
- Android platforms are located
- the file , including the set of callback functions in the Android framework
- the file , including all the attribute states
- the file , including
are explicitly defined. In this file, the crash point is defined by specifying the package, class, function signature and the line number of the last call in the application in the crash stack trace.
The application package kit is also declared in this file. 
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
The fuzzing tools are all available in a docker file that is extended from the docker provided by Themis. In this docker, we add all the fuzzing tools under the path . To run each fuzzing tool, one can use the themis.py to start an analysis:

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

