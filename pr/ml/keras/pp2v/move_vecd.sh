#!/bin/bash

DEST=/mnt/mx4t/epqe/image_desc/vec_geom/

echo "== $0  Moving .top to $DEST"

if [ ! -d $DEST ] ; then
    echo "=== $0 NOT A DIR: $DEST"
    exit 1
fi

ls *.top

echo === sleeping 5, then moving to $DEST

sleep 5

mv *.top $DEST
