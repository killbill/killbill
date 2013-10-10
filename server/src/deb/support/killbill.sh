#!/usr/bin/env bash

[ -r /etc/default/killbill-server ] && . /etc/default/killbill-server

JAVA=${JAVA-"`which java`"}

JAVA_PROPERTIES=${JAVA_PROPERTIES-"-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=12345 -Xms512m -Xmx1024m -XX:MaxPermSize=512m -XX:MaxDirectMemorySize=512m -XX:+UseConcMarkSweepGC"}

build_properties() {
    local opts=
    local prop=
    for prop in `cat  $KILLBILL_PROPERTIES | grep =`; do
        local k=`echo $prop | awk '  BEGIN {FS="="} { print $1 }'`
        local v=`echo $prop | awk 'BEGIN {FS="="} { print $2 }'`
        opts="$opts -D$k=$v"
    done
    echo $opts
}

$JAVA $JAVA_PROPERTIES `build_properties` -jar $KILLBILL_WAR
