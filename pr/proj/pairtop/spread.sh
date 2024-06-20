#!/bin/bash

FILE=$1

#MAXINT=2147483647
#MAXLONG=9223372036854775807

echo == unique values, zeros, maxints, maxlongs

echo == cv2l

F="${FILE}__cv2l"

awk '$3=="cv2l" {print $4}' $FILE > $F ; 

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == cv2r

F="${FILE}__cv2r"

awk '$3=="cv2r" {print $4}' $FILE > $F 

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == cv3l

F="${FILE}__cv3l"

awk '$3=="cv3l" {print $4}' $FILE > $F

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == cv3r

F="${FILE}__cv3r"

awk '$3=="cv3r" {print $4}' $FILE > $F

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == pv3l

F="${FILE}__pv3l"

awk '$3=="pv3l" {print $4}' $FILE > $F

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == pv3r

F="${FILE}__pv3r"

awk '$3=="pv3r" {print $4}' $FILE > $F

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == cv4l

F="${FILE}__cv4l"

awk '$3=="cv4l" {print $4}' $FILE > $F

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == cv4r

F="${FILE}__cv4r"

awk '$3=="cv4r" {print $4}' $FILE > $F

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == pv4l

F="${FILE}__pv4l"

awk '$3=="pv4l" {print $4}' $FILE > $F

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == pv4r

F="${FILE}__pv4r"

awk '$3=="pv4r" {print $4}' $FILE > $F

sort -u $F | wc -l 
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == cv5l

F="${FILE}__cv5l"

awk '$3=="cv5l" {print $4}' $FILE > $F

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == cv5r

F="${FILE}__cv5r"

awk '$3=="cv5r" {print $4}' $FILE > $F

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == pv5l

F="${FILE}__pv5l"

awk '$3=="pv5l" {print $4}' $FILE > $F

sort -u $F | wc -l 
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == pv5r

F="${FILE}__pv5r"

awk '$3=="pv5r" {print $4}' $FILE > $F

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == cv12l

F="${FILE}__cv12l"

awk '$3=="cv12l" {print $4}' $FILE > $F

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == cv12r

F="${FILE}__cv12r"

awk '$3=="cv12r" {print $4}' $FILE > $F

sort -u $F | wc -l   
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == pv12l

F="${FILE}__pv12l"

awk '$3=="pv12l" {print $4}' $FILE > $F

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l

echo == pv12r

F="${FILE}__pv12r"

awk '$3=="pv12r" {print $4}' $FILE > $F

sort -u $F | wc -l
awk '$1==0' $F | wc -l
awk '$1==2147483647' $F | wc -l
awk '$1==9223372036854775807' $F | wc -l
