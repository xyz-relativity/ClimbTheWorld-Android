package com.climbtheworld.app.storage.database;

import com.climbtheworld.app.utils.GeoUtils;

import java.util.List;
import java.util.Map;

public abstract class OsmComposedEntity extends OsmEntity {
	public double centerDecimalLatitude = 0;
	public double centerDecimalLongitude = 0;

	public double bBoxNorth = 0;
	public double bBoxEast = 0;
	public double bBoxSouth = 0;
	public double bBoxWest = 0;

	protected void computeCache(List<OsmNode> osmNodes, Map<Long, OsmNode> nodeCache) {
		if (osmNodes.size() == 0) {
			return;
		}

		bBoxNorth = bBoxSouth = osmNodes.get(0).decimalLatitude;
		bBoxEast = bBoxWest = osmNodes.get(0).decimalLongitude;

		for (OsmNode crNode: osmNodes) {
			this.centerDecimalLatitude += crNode.decimalLatitude;
			this.centerDecimalLongitude += crNode.decimalLongitude;
			if (bBoxNorth < crNode.decimalLatitude) {
				bBoxNorth = crNode.decimalLatitude;
			}
			if (bBoxSouth > crNode.decimalLatitude) {
				bBoxSouth = crNode.decimalLatitude;
			}
			if (bBoxEast < crNode.decimalLongitude) {
				bBoxEast = crNode.decimalLongitude;
			}
			if (bBoxWest > crNode.decimalLongitude) {
				bBoxWest = crNode.decimalLongitude;
			}
		}

		// take care of ante-meridian
		if ((bBoxEast < 0 != bBoxWest < 0) && GeoUtils.diffAngle(bBoxEast, bBoxWest) > 0) {
			double tmp = bBoxEast;
			bBoxEast = bBoxWest;
			bBoxWest =tmp;
		}

		this.centerDecimalLatitude = this.centerDecimalLatitude / osmNodes.size();
		this.centerDecimalLongitude = this.centerDecimalLongitude / osmNodes.size();
	}
}
