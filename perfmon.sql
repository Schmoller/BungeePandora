DROP TABLE IF EXISTS `UpstreamStats`;
DROP TABLE IF EXISTS `DownstreamStats`;

CREATE TABLE `UpstreamStats` (`protocol_id` INTEGER NOT NULL, `packet_id` INTEGER NOT NULL, `timeslot` DATETIME NOT NULL, `updated` TIMESTAMP NOT NULL, `direction` ENUM('INBOUND', 'OUTBOUND') NOT NULL, `count` INTEGER NOT NULL, `size` BIGINT NOT NULL, `active_connections` INTEGER NOT NULL, PRIMARY KEY (`protocol_id`, `packet_id`, `timeslot`,`direction`));
CREATE TABLE `DownstreamStats` (`protocol_id` INTEGER NOT NULL, `packet_id` INTEGER NOT NULL, `timeslot` DATETIME NOT NULL, `updated` TIMESTAMP NOT NULL, `server` VARCHAR(20) NOT NULL, `direction` ENUM('INBOUND', 'OUTBOUND') NOT NULL, `count` INTEGER NOT NULL, `size` BIGINT NOT NULL, PRIMARY KEY (`protocol_id`, `packet_id`, `timeslot`,`direction`,`server`));