package edu.ysu.itrace;

public class ScEntity {

	/**
	 * Types of source code entities.
	 */
	public enum SCEType {
		QA,TYPE, METHOD, VARIABLE, COMMENT, ENUM, IMPORT, FORSTATEMENT, METHOD_INVOCATION, IFSTATEMENT, WHILESTATEMENT, SWITCHSTATEMENT, CONDITIONAL_EXPRESSION, FEATURE, SCENARIO, GIVEN, AND, WHEN, BUT, THEN, SCENARIO_OUTLINE, BACKGROUNDS, TAGS
	}

	public enum SCEHow {
		QA,DECLARE, USE, LINE_COMMENT, BLOCK_COMMENT, DOC_COMMENT
	}

	public SCEType type;
	public SCEHow how;
	public String name;
	public int totalLength;
	public int startLine, endLine;
	public int startCol, endCol;

	public SCEType getType() {
		return type;
	}

	public void setType(SCEType type) {
		this.type = type;
	}

	public SCEHow getHow() {
		return how;
	}

	public void setHow(SCEHow how) {
		this.how = how;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getTotalLength() {
		return totalLength;
	}

	public void setTotalLength(int totalLength) {
		this.totalLength = totalLength;
	}

	public int getStartLine() {
		return startLine;
	}

	public void setStartLine(int startLine) {
		this.startLine = startLine;
	}

	public int getEndLine() {
		return endLine;
	}

	public void setEndLine(int endLine) {
		this.endLine = endLine;
	}

	public int getStartCol() {
		return startCol;
	}

	public void setStartCol(int startCol) {
		this.startCol = startCol;
	}

	public int getEndCol() {
		return endCol;
	}

	public void setEndCol(int endCol) {
		this.endCol = endCol;
	}

	public String createName() {
		return name;
	}

	public void print() {
		System.out.println("works");
	}
}
