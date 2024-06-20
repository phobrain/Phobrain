#!/bin/bash

# debugging script

FILE=$1

echo == unique values, zeros, maxints



awk '{print $4}' $FILE |sort -n > xnums
#VAL0=`head -1 xnums`

echo "------"
head -4 xnums
echo "..."
tail -4 xnums
echo "------"

echo "-- total rows"
wc -l xnums
echo "repeats of first"
uniq -c xnums | head -1
echo "-- uniq"
uniq -c xnums |wc -l
echo "-- zero"
awk '$1==0' xnums | wc -l

#awk '$1==2147483647' xnums | wc -l
