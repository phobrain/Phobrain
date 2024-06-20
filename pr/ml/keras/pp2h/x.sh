#!/bin/bash

DEST=/mnt/mx4t/epqe/image_desc/vec_geom/

echo "== $0 calcing vec distancs"

# $ vec_dist.py x.vecs <poinca|cosine>

echo "== $0  doing lrv_5 and _12 - 8 procs"

vec_dist.py v_2022_10/JH5/VGG16/m_v_model_pnlv_95_91_1739_2097__reb_4_2_150__min_34_ki_glorot_normal_Hmx_ml1984_1984_VGG16_224v_lrv_5_nathan_relu_STEEP_SGD.vecs cosine &
vec_dist.py v_2022_10/JH5/VGG16/m_v_model_pnlv_95_91_1739_2097__reb_4_2_150__min_34_ki_glorot_normal_Hmx_ml1984_1984_VGG16_224v_lrv_5_nathan_relu_STEEP_SGD.vecs poinca &

vec_dist.py v_2022_11/JH12/VGG16/m_v_model_pnlv_93_92_1416_1952__reb_6_1_250__min_38_ki_glorot_normal_Hmx_ml1984_VGG16_224v_lrv_12_nathan_relu_SGD.vecs cosine &
vec_dist.py v_2022_11/JH12/VGG16/m_v_model_pnlv_93_92_1416_1952__reb_6_1_250__min_38_ki_glorot_normal_Hmx_ml1984_VGG16_224v_lrv_12_nathan_relu_SGD.vecs poinca &

wait

echo "== $0 DONE - if likey, mv to: $DEST"
ls -l *.top
du -sh *.top
