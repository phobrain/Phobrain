#!/bin/bash

ls $1 | grep .h5 | sed -e 's:m_v_model::g' | sed -e 's:_ki_0_Hmx_g128_s128_r12_1984_VGG16_224v: :g' | sed -e 's:_nathan: :g'
