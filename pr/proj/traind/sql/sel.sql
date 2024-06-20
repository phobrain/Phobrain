/*
 *  SPDX-FileCopyrightText: 2022 Bill Ross <phobrain@sonic.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-or-later
 */

SELECT id, 
    width, height, 
    frac_dim, frac_dev, frac_r2,
    r, g, b, rgb_radius, ll, aa, bb, lab_radius, lab_contrast,
    face, blur, sign, number, 
    density, sum_d0, sum_d0_l, sum_d0_r, 
    ang_ab, d_ctr_rgb, d_ctr_ab, d_ctr_8d, d_ctr_27d, d_ctr_64d
FROM picture ORDER BY id;
