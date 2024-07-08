#!/bin/sh

for i in `seq 500`; do
	curl -X POST -d "url=$i" localhost:8080
done
