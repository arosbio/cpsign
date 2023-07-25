#!/bin/bash

# This script runs a check that version is not "SNAPSHOT", performs a build clean, test, 
# package and javadoc check, aiming at pre-verifying the code is ready for deployment to maven central.
# Accepts an optional flag "--skip-version|--skip-version-check that omitts the SNAPSHOT checking

# Default behavior (without the flag)
check_version=true

# Check if the flag "--skip-version" or "--skip-version-check" is provided
if [[ "$*" == *"--skip-version"* || "$*" == *"--skip-version-check"* ]]; then
    check_version=false # Turn of version check
fi

if [ "$check_version" = true ] ; then
    # Get the project.version from the POM
    PROJECT_VERSION=$(mvn -q \
        -Dexec.executable=echo \
        -Dexec.args='${project.version}' \
        --non-recursive \
        exec:exec)

    # Check if the "PROJECT_VERSION" ends with "SNAPSHOT"
    if [[ "$PROJECT_VERSION" == *SNAPSHOT ]]; then
        echo "
        
    ==============================================================================================
    Error: Project version ends with 'SNAPSHOT': run 'mvn versions:set -DnewVersion={new version}'
        to set a valid version that can be deployed 
    ==============================================================================================
        
        "
        exit 1
    fi
else
    echo "
!!! Version check is _NOT_ performed - please check version is not SNAPSHOT before actual deployment !!!
    "
    sleep 5 # Sleep for 5 seconds to make sure the user can see the info
fi

mvn clean package -P fatjar 
status=$?
if [ $status -ne 0 ]; then
    echo "
    
    =====================================================================================
    Failed compling/testing/packaging cpsign - resolve the above issues before deployment
    =====================================================================================
    
    "
    exit $status
fi

# Check javadoc is OK
mvn javadoc:aggregate-jar 

status=$?
if [ $status -ne 0 ]; then
    echo "
    
    ======================================================================"
    Failed generating javadoc - resolve the above issues before deployment"
    ======================================================================
    
    "
    exit $status
fi
