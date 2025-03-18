export interface Token {
  type: string;
  value: string;
  line: number;
  position: number;
}

export interface Node {
  type: string;
  value: string;
  children: Node[];
}

export interface AnalysisResult {
  lexicalResult: Token[];
  syntaxResult: Node;
}