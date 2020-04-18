#!/usr/bin/env bash
version=1.0.27-RELEASE
application_name=data-service-yun-pan
jar_file=$application_name-$version.jar
pid=`ps -ef | grep $jar_file | grep -v "grep" | awk '{print int($2)}'`
for id in $pid
do
kill -9 $id
echo "$application_name:进程id-$id killed!"
done
javaParamters='-Xms1024M -Xmx1024M -XX:NewSize=700M -XX:MaxNewSize=700M -XX:MetaspaceSize=150M'
debugParamters='-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8880'
nohup java -jar $javaParamters $jar_file -start >./$application_name.out 2>&1 &


# nohup java -jar  -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDetails -XX:+PrintGCDateStamps  data-service-yun-pan-1.0.27-RELEASE.jar -start & echo $!