
#!/bin/bash

# (0)
# get fuzzing tool name
tool=$1
# get  directory path containing the apps in the host
targetDir=$2
# get number of tests to run
count=$3

if [ $tool == "monkey" ]; then
  echo "[Testing tool] monkey is selected"
elif [ $tool == "ape" ]; then
  echo "[Testing tool] ape is selected"
elif [ $tool == "stoat" ]; then
  echo "[Testing tool] stoat is selected"
elif [ $tool == "fastbot2" ]; then
  echo "[Testing tool] fastbot2 is selected"
else
  echo "[Testing tool] inappropriate testing tool is entered"
fi

if [ -d $targteDir ]; then
  echo "[Test Setting] target directory exists"
else
  echo "[Test Setting] target directory doesn't exist"
  exit -1
fi

if [ $count -ge 0 ]; then
  echo "[Test Setting] $count tests will be performed on each app"
else
  echo "[Test Setting] $count should be a positive number"
  exit -1
fi

counter=7
# (1) get the list of all apk files in the target directory, and run a separate docker for them
for file in $targetDir/*.apk
do
    let counter++
    # (2) add session name
    fileName=$(basename $file .apk)
    echo "[Test Setting] Let's fuzz $fileName"
    session="session$counter"
    echo "[Test Setting] Its docker session is$session"

    # (3) does the docker already exist?
    if [[ -z $(tmux ls -F "#(session_name" 2>/dev/null | grep "^$sessions$") ]]; then
        tmux $TMUX_OPTS new-session -s $session -d
    else
        tmux $TMUX_OPTS attach -t $session
        exit 0
    fi
    for (( i=1; i<=$count; i++ ))
      do
            window=$i
            tmux new-window -n $window -d
            dockerName="$(echo baseline-$fileName-$i-$tool | tr -d "." | tr -d "#" ) "
            # (4) execute the command : 
            echo "Running test with "$tool" on "$fileName
            tmux send-keys -t $session:$window "docker run --device=/dev/kvm --cpus="4" -i --name="$dockerName" -t mole-themis  /bin/bash -i -c 'source ~/.bashrc && emulator -avd Android7.1 -no-audio -no-window -read-only & sleep 5 && cd /home/Themis/scripts  && ./sign.sh /home/apps/$fileName.apk key "android" &&  python3 themis.py --avd Android7.1 --apk /home/apps/$fileName.apk --time 6h --repeat 5 -o /home/output --$tool'" 'C-m'
      done

done

