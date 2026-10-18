--POSGRE SQL
	-- data.sql
	-- Clear existing data first (optional)
	-- DELETE FROM VideoContainsArtist;
	-- DELETE FROM Video;
	-- DELETE FROM Artist;
	-- DELETE FROM Instrument;
	-- DELETE FROM Duration;
	-- DELETE FROM Type;
	-- DELETE FROM Quote;
	-- DELETE FROM AlbumContainsArtist;
	-- DELETE FROM Album;
	
	--instrument
	INSERT INTO Instrument (instrument_id, instrument_name) VALUES (1, 'Bass');
	INSERT INTO Instrument (instrument_id, instrument_name) VALUES (2, 'Guitar');
	INSERT INTO Instrument (instrument_id, instrument_name) VALUES (3, 'Piano');
	INSERT INTO Instrument (instrument_id, instrument_name) VALUES (4, 'Drums');
	INSERT INTO Instrument (instrument_id, instrument_name) VALUES (5, 'Voice');
	INSERT INTO Instrument (instrument_id, instrument_name) VALUES (6, 'Sax');
	INSERT INTO Instrument (instrument_id, instrument_name) VALUES (7, 'Trumpet');
	INSERT INTO Instrument (instrument_id, instrument_name) VALUES (8, 'Violin');
	INSERT INTO Instrument (instrument_id, instrument_name) VALUES (9, 'Vibes');
	INSERT INTO Instrument (instrument_id, instrument_name) VALUES (10, 'Clarinete');
	INSERT INTO Instrument (instrument_id, instrument_name) VALUES (11, 'Trombone');
	INSERT INTO Instrument (instrument_id, instrument_name) VALUES (12, 'Journalism');
	INSERT INTO Instrument (instrument_id, instrument_name) VALUES (13, 'other');
	INSERT INTO Instrument (instrument_id, instrument_name) VALUES (99, 'NA');

	
	SELECT setval('instrument_instrument_id_seq', (SELECT max(instrument_id) FROM instrument));

	--duration;
	INSERT INTO Duration (duration_id, duration_name, duration_description) VALUES (1, 'very short', '5');
	INSERT INTO Duration (duration_id, duration_name, duration_description) VALUES (2, 'short', '5to15');
	INSERT INTO Duration (duration_id, duration_name, duration_description) VALUES (3, 'medium', '15to30');
	INSERT INTO Duration (duration_id, duration_name, duration_description) VALUES (4, 'long', '30to60');
	INSERT INTO Duration (duration_id, duration_name, duration_description) VALUES (5, 'very long', 'moreThan60');
	
	SELECT setval('duration_duration_id_seq', (SELECT max(duration_id) FROM duration));
	
	--type;
	INSERT INTO type (type_id, type_name) VALUES (1, 'interview');
	INSERT INTO type (type_id, type_name) VALUES (2, 'movie');
	INSERT INTO type (type_id, type_name) VALUES (3, 'lecture');
	INSERT INTO type (type_id, type_name) VALUES (4, 'music');
	
	SELECT setval('type_type_id_seq', (SELECT max(type_id) FROM type));
	
	
	
	