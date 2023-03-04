package com.climbtheworld.app.storage.database;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

public class NodesWithWays {
	@Embedded
	public GeoNode node;
	@Relation(
			entity = OsmWay.class,
			parentColumn = "osmID",
			entityColumn = "osmID"
	)
	public List<OsmWay> ways;

}
