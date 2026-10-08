#!/bin/sh
# THROWAWAY SPIKE run script
W=/Users/pete/IdeaProjects/wfm-service/.claude/worktrees/agent-ae66e7f95ac5c8f0c
CP=`cat $W/build/spike-cp.txt`
java -cp "$W/build/spike-classes:$CP" com.wfm.spike.SpikeMain 2>&1
