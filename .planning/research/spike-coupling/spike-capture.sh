#!/bin/sh
W=/Users/pete/IdeaProjects/wfm-service/.claude/worktrees/agent-ae66e7f95ac5c8f0c
sh $W/spike-run.sh > $W/build/spike-output.txt 2>&1
echo "captured"
