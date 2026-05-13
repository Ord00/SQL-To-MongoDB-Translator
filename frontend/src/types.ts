export interface Token {
    lexeme: string;
    category: string;
    line?: number;
    position?: number;
}

export interface ASTNode {
    nodeType: string;
    token?: Token;
    children: ASTNode[];
}

export interface AnalysisResult {
    lexicalResult: Token[];
    syntaxResult: ASTNode;
    ir: any; // Можно уточнить тип при необходимости
    mongoCode: string;
}