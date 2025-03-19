\o dump0.csv
\f ','
\a
SELECT browser_id,id,vertical,
        id1, id2, flow_rating, order_in_session,
        big_stime, big_time, load_time, 
        user_time, user_time2,
        mouse_down_time, mouse_time,
        mouse_dist, mouse_dist2,
        dot_count, mouse_dx, mouse_dy,
        mouse_vecx, mouse_vecy, dot_vec_len,
        mouse_maxv, mouse_maxa, mouse_mina, mouse_maxj,
        dot_max_vel, dot_max_acc, dot_max_jerk,
        dot_start_scrn, dot_end_scrn, dot_vec_ang
FROM pr.feeling_pair
WHERE id > 85 
    AND vertical is false 
    AND flow_rating in (0,1,2)
ORDER BY browser_id, id;
\o
