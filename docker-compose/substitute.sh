#!/bin/bash
set -eux

template="/${BENCHMARK}-config-template.xml"
out="/${BENCHMARK}-config.xml"

cp $template $out
sed -i "s/BB_ADDRESS/${ADDRESS}/" $out
sed -i "s/BB_DATABASE/${DATABASE}/" $out 
sed -i "s/BB_USERNAME/${DBUSER}/" $out 
sed -i "s/BB_PASSWORD/${PASSWORD}/" $out 
sed -i "s/BB_REGION/${REGION}/" $out 
sed -i "s/BB_NUM_REGIONS/${NUM_REGIONS}/" $out