CREATE DATABASE iTrace;

CREATE TABLE session_info 
(
    session_ID varchar(255),
    session_purpose varchar(255),
    session_descrip varchar(255),
    developer_username varchar(255),
    developer_name varchar(255), 
    screen_width int,
    screen_height int,
    PRIMARY KEY (session_ID)
);

CREATE TABLE gazes
(
    gaze_id int AUTO_INCREMENT,
    gaze_session_id varchar(255),
    gaze_name varchar(255),
    gaze_type varchar(255),
    x int,
    y int,
    left_validation float,
    right_validation float,
    left_pupil_diameter float,
    right_pupil_diameter float,
    timestamp varchar(255),
    session_time bigint,
    tracker_time bigint,
    system_time bigint,
    nano_time bigint,
    path varchar(255),
    line_height int,
    font_height int,
    line int,
    col int,
    line_base_x int,
    line_base_y int,
    PRIMARY KEY (gaze_id),
    FOREIGN KEY (gaze_session_id) REFERENCES session_info(session_ID)

);

CREATE TABLE sces
(
    sce_gaze_id int,
    sce_name varchar(255),
    sce_type varchar(255),
    how varchar(255),
    total_length int,
    start_line int,
    end_line int,
    start_col int,
    end_col int,
    depth int,
    FOREIGN KEY (sce_gaze_id) REFERENCES gazes(gaze_id)

);



