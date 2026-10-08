#!/bin/sh
# THROWAWAY SPIKE build/run script
set -e
W=/Users/pete/IdeaProjects/wfm-service/.claude/worktrees/agent-ae66e7f95ac5c8f0c
CP=`cat $W/build/spike-cp.txt`
mkdir -p $W/build/spike-classes
javac -nowarn -d $W/build/spike-classes -cp "$CP" $W/src/test/java/com/wfm/spike/*.java
echo "COMPILE OK"
