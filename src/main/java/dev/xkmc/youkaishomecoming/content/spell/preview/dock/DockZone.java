package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

/**
 * 停靠区域枚举。当拖拽面板到目标 DockGroup 上方时，
 * 根据鼠标在目标区域内的相对位置判定停靠方向。
 */
public enum DockZone {
	CENTER,
	LEFT,
	RIGHT,
	TOP,
	BOTTOM;

	private static final float EDGE_RATIO = 0.2f;

	/**
	 * 根据鼠标在目标 DockGroup 区域内的相对位置，判定停靠区域。
	 *
	 * @param relX 鼠标相对于 group 左上角的 X 坐标
	 * @param relY 鼠标相对于 group 左上角的 Y 坐标
	 * @param w    group 宽度
	 * @param h    group 高度
	 * @return 停靠区域
	 */
	public static DockZone detect(double relX, double relY, int w, int h) {
		double nx = relX / w;
		double ny = relY / h;

		if (ny < EDGE_RATIO) return TOP;
		if (ny > 1.0 - EDGE_RATIO) return BOTTOM;
		if (nx < EDGE_RATIO) return LEFT;
		if (nx > 1.0 - EDGE_RATIO) return RIGHT;
		return CENTER;
	}
}
