#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

set -e

PHOB_HOME=`../../bin/get_phobrain_home.sh`

JETTY_HOME=`../../bin/get_jetty_home.sh`

#------------------------------------------------

echo "============================================="
echo Phobrain server started from: `pwd`
echo
echo JETTY: $JETTY_HOME  $JETTY_HOME/start.jar
echo
echo PHOBRAIN: $PHOB_HOME/pr_git/pr/jetty-base contents: `ls $PHOB_HOME/pr_git/pr/jetty-base`
echo
echo In webapps:
ls -l $PHOB_HOME/pr_git/pr/jetty-base/webapps
echo
echo
export JETTY_LOGS=$PHOB_HOME/logs
echo Logging: $JETTY_LOGS
echo
#echo starting in 5..
#sleep 5

# Setup: manually:
#
# $ java -jar $JETTY_HOME/start.jar --add-to-start=logging-logback

java -Xmx15g \
  -jar $JETTY_HOME/start.jar \
    ‐Dlog4j2.formatMsgNoLookups=True \
    org.eclipse.jetty.LEVEL=DEBUG \
    jetty.home=$JETTY_HOME \
    jetty.console-capture.dir=$JETTY_LOGS \
    jetty.base=$PHOB_HOME/pr_git/pr/jetty-base --module=gzip 

    //-Dorg.eclipse.jetty.util.log.class=org.eclipse.jetty.util.log.Slf4jLog \
# --dry-run

#--create-startd --add-to-start=jndi
