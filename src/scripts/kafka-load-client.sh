#!/usr/bin/env

delay=$1
path_to_avro_file=$2

while true
do
  kcat -b localhost:29092 -t externalservice.events -P $path_to_avro_file
	sleep $delay
done