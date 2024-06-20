#!/bin/bash

awk $STMT pr.pairtop_vec_v_dump.sql | sort -n | uniq -c | awk '{print $2,$1}' | sort -n > "$TAG"
