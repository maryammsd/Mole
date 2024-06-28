#!/bin/bash


targetDir=$1

# (1) Create an associative array to store the crash list 
declare -A keyarray

keyarray["aphotomanager-116"]="java.lang.RuntimeException: Unable to start activity ComponentInfo{de.k3b.android.androFotoFinder/de.k3b.android.androFotoFinder.SettingsActivity}: android.view.InflateException: Binary XML file line|android.app.ActivityThread.performLaunchActivity|android.app.ActivityThread.handleLaunchActivity"
keyarray["activitydiary-118"]="java.lang.IllegalArgumentException: position \(0\) too small|de.rampro.activitydiary.ui.generic.DetailRecyclerViewAdapter.getDiaryImageIdAt|de.rampro.activitydiary.ui.history.HistoryRecyclerViewAdapter\$1.onClick"
keyarray["activitydiary-285"]="java.lang.NumberFormatException: For input string: \"\"|java.lang.Integer.parseInt|java.lang.Integer.parseInt"
keyarray["amazefilemanager-1558"]="com.amaze.filemanager.exceptions.StreamNotFoundException: Can't get stream|com.amaze.filemanager.asynchronous.asynctasks.WriteFileAbstraction.doInBackground|com.amaze.filemanager.asynchronous.asynctasks.WriteFileAbstraction.doInBackground"
keyarray["amazefilemanager-1656"]="java.lang.String.substring|com.amaze.filemanager.utils.files.FileUtils.getPathsInPath"
keyarray["amazefilemanager-1796"]="java.lang.IndexOutOfBoundsException: Index: 0, Size: 0|java.util.ArrayList.get|com.amaze.filemanager.asynchronous.asynctasks.DeleteTask.onPostExecute"
keyarray["amazefilemanager-1232"]="Caused by: java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.Object java.util.ArrayList.get\(int\)' on a null object reference|com.amaze.filemanager.activities.MainActivity.onActivityResult|android.app.Activity.dispatchActivityResult"
keyarray["amazefilemanager-1837"]="java.lang.IndexOutOfBoundsException:|java.util.ArrayList.get|com.amaze.filemanager.adapters.glide.RecyclerPreloadModelProvider.getPreloadItems"
keyarray["ankidroid-5756"]="java.lang.RuntimeException: Unable to start activity ComponentInfo{com.ichi2.anki/com.ichi2.anki.Reviewer}: java.lang.RuntimeException|android.app.ActivityThread.performLaunchActivity|android.app.ActivityThread.handleLaunchActivity"
keyarray["ankidroid-4977"]="java.lang.NullPointerException: Attempt to invoke virtual method 'boolean android.support.v7.widget.SearchView.isIconified()' on a null object reference|com.ichi2.anki.CardBrowser\$20.onPostExecute|com.ichi2.async.DeckTask\$TaskListener.onPostExecute"
keyarray["ankidroid-4200"]="java.lang.RuntimeException: An error occurred while executing doInBackground()|android.os.AsyncTask\$3.done|java.util.concurrent.FutureTask.finishCompletion"
keyarray["ankidroid-6145"]="java.lang.RuntimeException: An error occurred while executing doInBackground()|android.os.AsyncTask\$3.done|java.util.concurrent.FutureTask.finishCompletion"
keyarray["ankidroid-4451"]="java.lang.ClassCastException: android.support.design.widget.CoordinatorLayout\$LayoutParams cannot be cast to android.widget.RelativeLayout\$LayoutParam|com.ichi2.anki.AbstractFlashcardViewer.initLayout|com.ichi2.anki.AbstractFlashcardViewer.onCollectionLoaded"
keyarray["ankidroid-4707"]="android.os.FileUriExposedException:|android.os.StrictMode.onFileUriExposed|android.net.Uri.checkFileUriExposed|android.content.ClipData.prepareToLeaveProcess"
keyarray["ankidroid-5638"]="java.lang.ArrayIndexOutOfBoundsException: Array index out of range:|java.util.regex.Matcher.appendEvaluated|java.util.regex.Matcher.appendReplacement|com.ichi2.libanki.Utils.entsToTxt|com.ichi2.libanki.Utils.stripHTML"
keyarray["firefoxlite-4881"]="org.json.JSONException: End of input at character|org.json.JSONTokener.syntaxError|org.json.JSONTokener.nextValue|org.json.JSONArray.<init>|org.json.JSONArray.<init>|org.mozilla.rocket.util.JsonUtilsKt.toJsonArray|org.mozilla.rocket.home.contenthub.data.ContentHubRepoKt.jsonStringToTypeList"
keyarray["firefoxlite-4942"]="java.lang.RuntimeException: Cannot create an instance of class org.mozilla.rocket.home.HomeViewModel|androidx.lifecycle.ViewModelProvider\$NewInstanceFactory.create|androidx.lifecycle.ViewModelProvider.get|androidx.lifecycle.ViewModelProvider.get|org.mozilla.focus.tabs.tabtray.TabTrayFragment.onCreateView"
keyarray["firefoxlite-5085"]="java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.String android.os.BaseBundle.getString(java.lang.String)' on a null object reference|org.mozilla.focus.settings.SettingsFragment.onCreate|android.app.Fragment.performCreate"
keyarray["frost-1323"]="|java.net.UnknownHostException: Unable to resolve host \"m.facebook.com\": No address associated with hostname|java.net.InetAddress.lookupHostByName|java.net.InetAddress.getAllByNameImpl"
keyarray["materialfbook-224"]="android.content.res.Resources\$NotFoundException|android.content.res.ResourcesImpl.getValue|android.content.res.Resources.getInteger|org.chromium.ui.base.DeviceFormFactor.b"
keyarray["omni-377"]="java.lang.NumberFormatException: Invalid long:|java.lang.Long.invalidLong|java.lang.Long.parseLong|java.lang.Long.parseLong|it.feio.android.omninotes.DetailFragment.lambda\$initViewReminder\$2"
keyarray["omni-745"]="java.lang.NoSuchMethodError: No virtual method fitCenter()Lcom/bumptech/glide/request/RequestOptions|it.feio.android.simplegallery.GalleryPagerFragment.onCreateView|androidx.fragment.app.Fragment.performCreateView"
keyarray["phonograph-112"]="java.lang.RuntimeException: Unable to start activity ComponentInfo{com.kabouzeid.gramophone/com.kabouzeid.gramophone.appshortcuts.AppShortcutLauncherActivity}:|com.kabouzeid.gramophone.appshortcuts.AppShortcutLauncherActivity.startServiceWithSongs|com.kabouzeid.gramophone.appshortcuts.AppShortcutLauncherActivity.onCreate"
keyarray["scarlet-114"]="java.lang.Exception: Invalid Search Mode|com.maubis.scarlet.base.support.SearchConfigKt.getNotesForMode|com.maubis.scarlet.base.support.SearchConfigKt.filterSearchWithoutFolder|com.maubis.scarlet.base.support.SearchConfigKt.unifiedSearchSynchronous"
keyarray["wordpress-10302"]="java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.String org.wordpress.android.fluxc.model.SiteModel.getMobileEditor()' on a null object reference|org.wordpress.android.util.SiteUtils.isBlockEditorDefaultForNewPost|org.wordpress.android.ui.accounts.HelpActivity\$Companion.createIntent"
keyarray["wordpress-10363"]="java.lang.IllegalStateException: itemView.findViewById(R.id.container) must not be null|org.wordpress.android.ui.posts.PostListItemViewHolder.<init>|org.wordpress.android.ui.posts.PostListItemViewHolder.<init>"
keyarray["wordpress-10547"]="java.lang.IllegalArgumentException: PostLoadingState wrong value|org.wordpress.android.ui.posts.EditPostActivity\$PostLoadingState.fromInt|org.wordpress.android.ui.posts.EditPostActivity.onCreate"
keyarray["wordpress-11135"]="java.lang.IllegalStateException: siteStore.getSiteBySiteI…t.getLong(EXTRA_SITE_ID)) must not be null|org.wordpress.android.ui.CommentFullScreenDialogFragment.onCreateView|androidx.fragment.app.Fragment.performCreateView"
keyarray["wordpress-11992"]="java.lang.NullPointerException: Attempt to invoke virtual method 'boolean org.wordpress.android.ui.FilteredRecyclerView.isRefreshing()' on a null object reference|org.wordpress.android.ui.reader.ReaderPostListFragment.onSaveInstanceState|androidx.fragment.app.Fragment.performSaveInstanceState"
keyarray["wordpress-6530"]="org.greenrobot.eventbus.EventBusException: Invoking subscriber failed|org.greenrobot.eventbus.EventBus.handleSubscriberException|org.greenrobot.eventbus.EventBus.invokeSubscriber"
keyarray["wordpress-7182"]="org.greenrobot.eventbus.EventBusException: Invoking subscriber failed|org.greenrobot.eventbus.EventBus.handleSubscriberException|org.greenrobot.eventbus.EventBus.invokeSubscriber"
keyarray["wordpress-8659"]="java.lang.IllegalStateException: Two different ViewHolders have the same stable ID. Stable IDs in your adapter |ViewHolder 1:ViewHolder|View Holder 2:ViewHolder"
keyarray["and-261"]="java.lang.StackOverflowError:|java.util.HashMap.get|java.util.Collections|org.crosswire.jsword.book.sword.SwordBookMetaData.getProperty"
keyarray["and-375"]="kotlin.TypeCastException: null cannot be cast to non-null type org.crosswire.jsword.book.Book|net.bible.service.history.HistoryManager.setDumpString|net.bible.android.control.page.window.WindowRepository.restoreState"
keyarray["and-480"]="kotlin.KotlinNullPointerException|net.bible.service.db.bookmark.BookmarkDBAdapter.updateLabel|net.bible.android.control.bookmark.BookmarkControl.saveOrUpdateLabel"
keyarray["and-697"]="java.lang.RuntimeException: Unable to start activity ComponentInfo{net.bible.android.activity/net.bible.android.view.activity.mynote.MyNotes}|android.app.ActivityThread.performLaunchActivity|android.app.ActivityThread.handleLaunchActivity|android.app.ActivityThread.-wrap11|android.app.ActivityThread\$H.handleMessage"
keyarray["and-703"]="java.lang.NullPointerException: Attempt to invoke interface method 'org.crosswire.jsword.index.IndexStatus org.crosswire.jsword.book.Book.getIndexStatus(|net.bible.android.view.activity.search.SearchIndexProgressStatus.jobFinished|net.bible.android.view.activity.base.ProgressActivityBase.updateProgress"
keyarray["collect-3222"]="java.lang.IllegalArgumentException: column 'MAX(date)' does not exist|"
keyarray["geohashdroid-73"]="java.lang.RuntimeException: An error occurred while executing doInBackground()|android.os.AsyncTask\$3.done|java.util.concurrent.FutureTask.finishCompletion|java.util.concurrent.FutureTask.setException"
keyarray["nextcloud-1918"]="java.lang.ClassCastException: com.owncloud.android.ui.preview.PreviewImageActivity cannot be cast to com.owncloud.android.ui.activity.FileDisplayActivity|com.owncloud.android.ui.preview.PreviewImageFragment.onOptionsItemSelected|android.support.v4.app.Fragment.performOptionsItemSelected"
keyarray["nextcloud-4026"]="java.lang.IllegalStateException: This Activity already has an action bar supplied by the window decor.|androidx.appcompat.app.AppCompatDelegateImpl.setSupportActionBar|androidx.appcompat.app.AppCompatActivity.setSupportActionBar|com.owncloud.android.ui.activity.ToolbarActivity.setupToolbar"
keyarray["nextcloud-4792"]="java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.String com.owncloud.android.datamodel.OCFile.getRemotePath()'|com.owncloud.android.ui.dialog.CreateFolderDialogFragment.onClick|androidx.appcompat.app.AlertController\$ButtonHandler.handleMessage"
keyarray["nextcloud-5173"]="java.lang.NullPointerException: Attempt to invoke interface method 'android.view.MenuItem android.view.MenuItem.setVisible(boolean)'|com.owncloud.android.ui.fragment.OCFileListFragment.onPrepareOptionsMenu|androidx.fragment.app.Fragment.performPrepareOptionsMenu"
keyarray["commons-2123"]="java.lang.NullPointerException: Attempt to invoke virtual method 'android.support.v4.app.FragmentActivity android.support.v4.app.Fragment.getActivity()' on a null object reference|fr.free.nrw.commons.media.MediaDetailPagerFragment\$MediaDetailAdapter.getItem|android.support.v4.app.FragmentStatePagerAdapter.instantiateItem"
keyarray["commons-3244"]="java.lang.RuntimeException: Unable to start activity ComponentInfo{fr.free.nrw.commons/fr.free.nrw.commons.upload.UploadActivity}:|android.app.ActivityThread.performLaunchActivity|android.app.ActivityThread.handleLaunchActivity"
keyarray["commons-1391"]="java.lang.NullPointerException: Attempt to invoke virtual method 'boolean android.view.View.requestFocus()'|fr.free.nrw.commons.nearby.NearbyMapFragment.prepareViewsForSheetPosition|fr.free.nrw.commons.nearby.NearbyMapFragment\$8.onStateChanged"
keyarray["commons-1385"]="java.lang.NullPointerException: Callable returned null|io.reactivex.internal.functions.ObjectHelper.requireNonNull|io.reactivex.internal.operators.observable.ObservableFromCallable.subscribeActual"
keyarray["commons-1581"]="java.lang.NullPointerException: Attempt to invoke virtual method 'double android.location.Location.getLatitude()' on a null object reference|fr.free.nrw.commons.location.LatLng.from|fr.free.nrw.commons.location.LocationServiceManager.getLKL"
keyarray["open-2198"]="androidx.fragment.app.Fragment\$InstantiationException: Unable to instantiate fragment org.fossasia.openevent.general.search.type.SearchTypeFragment|androidx.fragment.app.Fragment.instantiate|androidx.fragment.app.FragmentContainer.instantiate"
keyarray["openlauncher-67"]="java.lang.SecurityException: Not allowed to change Do Not Disturb state|android.os.Parcel.readException|android.os.Parcel.readException"
keyarray["sunflower-239"]="java.lang.IllegalArgumentException: navigation destination com.google.samples.apps.sunflower|androidx.navigation.NavController.navigate|androidx.navigation.NavController.navigate"
keyarray["osmeditor4android-637"]="java.lang.NullPointerException: Attempt to invoke interface method 'java.util.Set java.util.Map.entrySet()' on a null object reference|de.blau.android.validation.BaseValidator.validateElement|de.blau.android.validation.BaseValidator.validate"
keyarray["osmeditor4android-729"]="android.view.ViewRootImpl\$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.|android.view.ViewRootImpl.checkThread|android.view.ViewRootImpl.invalidateChildInParent|android.view.ViewGroup.invalidateChild"

get_app_name() {
	local filename=$1
	name="${filename%%-*}"
	lowercase_name=$(echo "$name" | tr '[:upper:]' '[:lower:]')
	echo "$lowercase_name"
}

get_issue_id() {
	local filename=$1
	issueid=$(echo "$filename" | grep -oP '(?<=#)\d+(?=\.)')
	lowercase_issueid=$(echo "$issueid" | tr '[:upper:]' '[:lower:]')
	echo "$lowercase_issueid"
}

get_first_line(){
	local lines=$1
	numbers_array=()
	while IFS= read -r line || [[ -n "$line" ]]; do
  		numbers_array+=("$line")
	done <<< "$lines"
	echo "${numbers_array[0]}"
}


check_event_rate(){
	local directory=$1
	# (3-0) Getting the time
	time1=$(awk '$2 ~ /^[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}$/ { print $2; exit }' "$directory""found.log")
	time2=$(tac "$directory""found.log" | awk '$2 ~ /^[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}$/ { print $2; exit }')
	echo "[start time] $time1 [end time] $time2"
	# Convert time values to seconds since Unix epoch
	time1_sec=$(date -d "$time1" +%s.%3N)
	time2_sec=$(date -d "$time2" +%s.%3N)

	# Calculate the time difference between time2 and 24:00:00
	#diff_sec=$(echo "24 * 3600 - $time2_sec" | bc)

	# Check if the case where time1 is before 12:00 AM and time2 is after 12:00 AM exists
	if (( $(bc <<< "$time1_sec < $time2_sec") )); then
		# Add the difference to time1
		result_sec=$(echo "$time2_sec - $time1_sec" | bc)
	else
		# Calculate the time difference between time1 and 24:00:00
		diff_sec=$(echo "24 * 3600 - $time1_sec" | bc)

		# Add the difference to time2
		result_sec=$(echo "$diff_sec + $time2_sec" | bc)
	fi

	# Calculate hours, minutes, and seconds
	hours=$(date -d @$result_sec -u +%H)
	minutes=$(date -d @$result_sec -u +%M)
	seconds=$(date -d @$result_sec -u +%S.%3N)

	echo "Time difference: $hours hours, $minutes minutes, $seconds seconds"	# (3-1) If the 7th Column is <FUZZING>, then proceed to calculate the overhead
	# Fetch the 9th, 11th, 12th and 13th columns and save them in a file 
	# awk -F'[ ]' '$6 == "<FUZZING>:" {split($10, array, /[[:digit:]]+$/, seps);print $7,$8,$9,$10,array[1],seps[1]}' "$directory/found.log" > temp1.txt
	#head "$directory""found.log" -n 7
	#awk  -F'[ ]' '{print $6}' "$directory""found.log" > "$directory""temp0.txt"
	#awk '$6 == "<FUZZING>:" {split($10, array, /[[:digit:]]+$/, seps);print $7,$8,array[1],$9,seps[1]}' "$directory""found.log" > "$directory""temp1.txt"
	awk '$6 == "<FUZZING>:" && index($10, "$") == 0 {split($10, array, /[[:digit:]]+$/, seps); print $7, $8, array[1], $9, seps[1]}' "$directory""found.log" > "$directory""temp1.txt"
	#head "$directory/temp1.txt"
	countRejectedVerifier=$(grep -o -w -c "rejected" "$directory""found.log")
	echo "[rejected verifier] $countRejectedVerifier"
	echo "$directory"
	# (3-2) Count the number of irrelevant, necessary and rejected events 
	necessary=$(grep -o -w -c "necessary:" "$directory""temp1.txt")
	rejected=$(grep -c "^rejected" "$directory""temp1.txt")
	irrelevant=$(grep -o -w -c "irrelevant:" "$directory""temp1.txt")
	countN=$(((necessary/2)-rejected))
	countI=$(((irrelevant/2)+rejected))
	countR=$((rejected))
	echo "[necessary events] $countN"
	echo "[irrelevant events] $countI"
	echo "[rejected events] $countR"
	sub=$((countRejectedVerifier-count))
	echo "[rejected verifier] $sub"
	# (3-3) Calculate the overhead 
	# (3-4) Get the 13th Column and extract the fime from it : replace > with a space : it will be the 14th column
	# for each two row, subtract the 14th elements and save 9,11,12,13 and the result in a separate file
	awk 'NR%2 { split($5, a); next } { if ($5-a[1] > 0) printf "%d", $5-a[1]; print " ", $2, $3, $4 }' "$directory""temp1.txt" > "$directory""temp2.txt"
	awk '$0 ~ /irrelevant:/{print $1}' "$directory""temp2.txt" > "$directory""irrelevant.txt"
	awk '$0 ~ /necessary:/{print $1}' "$directory""temp2.txt" > "$directory""necessary.txt"
	irrelevantmax=$(sort -n -r "$directory""irrelevant.txt" | head -n1)
	irrelevantmin=$(sort -n -r "$directory""irrelevant.txt" | tail -n1)	
	irrelevantmedian=$(sort -n "$directory""irrelevant.txt" | awk ' { a[i++]=$1; } END { x=int((i+1)/2); if (x < (i+1)/2) print (a[x-1]+a[x])/2; else print a[x-1]; }') 
	necessarymax=$(sort -n -r "$directory""necessary.txt" | head -n1)
	necessarymin=$(sort -n -r "$directory""necessary.txt" | tail -n1)
	necessarymedian=$(sort -n "$directory""necessary.txt" | awk ' { a[i++]=$1; } END { x=int((i+1)/2); if (x < (i+1)/2) print (a[x-1]+a[x])/2; else print a[x-1]; }')
	echo "Statistics of the Overhead(ns) " > "$directory""basename.txt"
	echo "necessary: " >> "$directory""basename.txt"
	echo "Min: " $necessarymin "  Max: " $necessarymax "Median: " $necessarymedian #>> "$directory""basename.txt"
	cat "$directory""necessary.txt"  | grep -v "1$" | awk -F ', '  '{   sum=sum+$1 ; sumX2+=(($1)^2)} END { printf "Average: %f. Standard Deviation: %f \n", sum/NR, sqrt(sumX2/(NR) - ((sum/NR)^2) )}' #>> "$directory""basename.txt"
	echo "irrelevant Events: " >> "$directory""basename.txt"
	if [[ -z $irrelevantmin ]];then
		irrelevantmin=0
	fi
	if [[ -z $irrelevantmax ]];then
		irrelevantmax=0
	fi
	if [[ -z $irrelevantmedian ]];then
		irrelevantmax=0
	fi
	echo "Min: " $irrelevantmin "  Max: " $irrelevantmax "Median: " $irrelevantmedian #>> "$directory""basename.txt"
	if [[ $irrelevantmax != 0 ]]; then
		if [[ $irrelevantmin != 0  ]]; then
			cat "$directory""irrelevant.txt"  | grep -v "1$" | awk -F ', '  '{   sum=sum+$1 ; sumX2+=(($1)^2)} END { printf "Average: %f. Standard Deviation: %f \n", sum/NR, sqrt(sumX2/(NR) - ((sum/NR)^2) )}' #>> "$directory""basename.txt"
		fi
	fi
	rm "$directory""temp2.txt"
	rm "$directory""temp1.txt"
	#rm "$directory""necessary.txt"
	#rm "$directory""irrelevant.txt"
}

get_crash_log() {
	local appname=$1
  	local issueid=$2
  	local directory=$3
	
	key="$appname-$issueid"
	echo "[key] $key"
	# Check whether the crash stack trace with this issue id for this app exists in the array
	if [[ -v keyarray["$key"] ]]; then
		echo "[Process] let's look for the crash in the log file."
		value=("${keyarray["$key"]}")
		IFS='|' read -ra stacktrace <<< "$value"
  		#for element in "${stacktrace[@]}"; do
    		#	echo "Element: $element"
  		#done

		# Get the first element from values array
		local first_element="${stacktrace[0]}"
		echo "[First Element] $first_element"
		
		# Find the line that contains the first element in the file
		local output=$(grep -n "$first_element" "$directory/logcat.log" | cut -d ':' -f 1)
		if [[ -z $output ]]; then
			echo "[Not Found] Crash not found in the file ."
		else
			# echo "[Lines] Found crash at lines $output"
			line=$(get_first_line $output)
			#echo "[lines] $line"
			# If the line is found, check subsequent elements
			if [[ -n "$line" ]]; then
				echo "[Found] First line of the crash is Found at line $line"
				local next_line=$((line + 1))
				local result="true"
				# Loop through the remaining elements and check if they exist in the next lines
				for (( i = 1; i < ${#stacktrace[@]}; i++ )); do
					local current_element="${stacktrace[i]}"
					local next_element=$(sed -n "${next_line}p" "$directory/logcat.log")
					#echo "[next element] $current_element"
					#echo "[next line content] $next_line $next_element"
					
					if [[ "$next_element" != *"$current_element"* ]]; then
						result="false"
						break
					fi
					#echo "$i th element found "
					next_line=$((next_line + 1))
				done
				if [[ "$result" == "true" ]]; then
					echo "[Found] crash is found at time "
					head -n "$((line - 1))" < "$directory/logcat.log" > "$directory/found.log"
					check_event_rate "$directory"
				fi
				#echo "Elements ${stacktrace[*]} found in consecutive lines: $result"
			else
				echo "[Not Found] Crash not found in the file ."
			fi
		fi
		
	else
  		echo "[Warning] there is no crash stack trace for this issue id $issueid in $appname."
	fi

}


# (2) Does the target  directory have subdirectories?
if [ -d $targetDir ]; then
  echo "[Test Setting] target directory exists"
else 
  echo "[Test Setting] target directory doesn't exist."
  exit -1
fi

# (3) For each subdirectory, go inside and see if there is a logcat.log there 
for folder in $targetDir/*/; do
	#echo "[Checking] " $folder "directory state ... "
	echo "--------------------------------------------------------------------------------"
	if [ -d $folder ]; then
		basename=$(basename  $folder)
		#echo "[Directory] $basename is found"
		appname=$(get_app_name $basename)
		issueid=$(get_issue_id $basename)
		#echo "[app info] app name is $appname with issue id $issueid"
		if [ -f "$folder/logcat.log" ]; then
			# (3-1) Check the fuzzing tool and then look for the crash in the logcat.log
			# (3-2) If crash is found, save the content to that point in a separate file
			if [[ $basename == *"monkey"* ]]; then
				echo "[tool] monkey or fastbot"
				get_crash_log "$appname" "$issueid" "$folder"
			elif [[ $basename == *"ape"* ]]; then
				echo "[tool] ape"
				get_crash_log "$appname" "$issueid" "$folder"
			elif [[ $basename == *"stoat"* ]]; then
				echo "we are checking stoat ..."
			elif [[ $basename == *"fastbot"* ]]; then
				get_crash_log "$appname" "$issueid" "$folder"
			else
				echo "[Warning] tool is not among the set monkey, ape, stoat and fastbot"
				exit -1
			fi

			

		#else
			#echo "[app info] app name is $appname with issue id $issueid"
			#echo "[error] logcat.log not found"
		fi
	fi
done
	

# (4) If the output is empty, then no result
# (5) else, print out the min, max, average and standard deviation of the overhead in nano seconds

