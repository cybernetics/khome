package khome.core.entities.sensors

import khome.core.entities.AbstractEntity

abstract class AbstractSensorEntity(name: String) : AbstractEntity<Double>("sensor", name)