package dev.xkmc.youkaishomecoming.content.spell.editor;

public enum PhaseEditorSection {
	ON_ENTER("On Enter"),
	ON_TICK("On Tick"),
	ON_EXIT("On Exit"),
	TRANSITIONS("Transitions");

	private final String title;

	PhaseEditorSection(String title) {
		this.title = title;
	}

	public String title() {
		return title;
	}

	public boolean isTransitionSection() {
		return this == TRANSITIONS;
	}
}
