#!/bin/bash

FILE=$1

echo == unique values, zeros, maxints



echo == cv3l

F="${FILE}__cv3l"

awk '$3=="cv3l" {print $4}' $FILE > $F

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l

echo == cv3r

F="${FILE}__cv3r"

awk '$3=="cv3r" {print $4}' $FILE > $F

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
