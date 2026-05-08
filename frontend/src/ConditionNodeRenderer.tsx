import React from 'react';
import SubqueryTooltip from './SubqueryTooltip';

interface ConditionNodeRendererProps {
    node: any;
    depth: number;
    compact?: boolean;
    showAll?: boolean;
    onOpenModal?: (subquery: any) => void;
}

const ConditionNodeRenderer: React.FC<ConditionNodeRendererProps> = ({
                                                                         node,
                                                                         depth,
                                                                         compact = false,
                                                                         showAll = true,
                                                                         onOpenModal
                                                                     }) => {
    if (!node) return null;

    if (!showAll && depth > 2) {
        return <div className="text-gray-400 text-xs italic ml-4">...</div>;
    }

    // Обработка @type из JSON
    const nodeType = node.type || node['@type'];
    const children = node.children || [];

    // LinkNode (AND/OR) - проверяем по типу и наличию детей
    if (nodeType === 'link' || nodeType === 'AND' || nodeType === 'OR' ||
        (children.length > 0 && !node.operand && !node.value && !node.subquery && !node.subqueryIR)) {
        const linkType = (nodeType === 'AND' || nodeType === 'link') ? 'AND' : 'OR';
        const linkColor = linkType === 'AND' ? 'bg-blue-100 text-blue-700' : 'bg-orange-100 text-orange-700';

        if (compact) {
            return (
                <div className="mb-1">
                    <span className={`text-xs font-mono px-1.5 py-0.5 rounded ${linkColor}`}>
                        {linkType}
                    </span>
                    <div className="ml-4 space-y-1 mt-1">
                        {children.map((child: any, idx: number) => (
                            <ConditionNodeRenderer
                                key={idx}
                                node={child}
                                depth={depth + 1}
                                compact={compact}
                                showAll={showAll}
                                onOpenModal={onOpenModal}
                            />
                        ))}
                    </div>
                </div>
            );
        }

        return (
            <div className="mb-3">
                <div className="flex items-center gap-2 mb-2">
                    <span className={`text-xs font-mono px-2 py-0.5 rounded ${linkColor}`}>
                        {linkType}
                    </span>
                </div>
                <div className="ml-4 pl-3 border-l-2 border-gray-200 space-y-2">
                    {children.map((child: any, idx: number) => (
                        <ConditionNodeRenderer
                            key={idx}
                            node={child}
                            depth={depth + 1}
                            compact={compact}
                            showAll={showAll}
                            onOpenModal={onOpenModal}
                        />
                    ))}
                </div>
            </div>
        );
    }

    // ExistsCondition - проверяем по типу и наличию subquery
    if (nodeType === 'exists' || node.subquery !== undefined || node.subqueryIR !== undefined) {
        const subqueryData = node.subquery || (node.subqueryIR ? { subqueryIR: node.subqueryIR } : null);
        const isExists = node.isExists !== undefined ? node.isExists : true;

        // Если есть дети, это неправильная структура - игнорируем их
        if (children.length > 0 && compact) {
            // Просто показываем EXISTS без вложенных детей
            return (
                <div className="font-mono text-sm py-1">
                    <span className={`font-medium ${!isExists ? 'text-red-600' : 'text-purple-600'}`}>
                        {isExists ? 'EXISTS' : 'NOT EXISTS'}
                    </span>
                    {subqueryData && onOpenModal && (
                        <SubqueryTooltip subquery={subqueryData} onOpenModal={onOpenModal} />
                    )}
                </div>
            );
        }

        if (!onOpenModal) {
            return (
                <div className="font-mono text-sm py-2">
                    <span className={`font-medium ${!isExists ? 'text-red-600' : 'text-purple-600'}`}>
                        {isExists ? 'EXISTS' : 'NOT EXISTS'}
                    </span>
                    {subqueryData && <span className="text-xs text-gray-400 ml-2">(подзапрос)</span>}
                </div>
            );
        }

        return (
            <div className="font-mono text-sm py-2">
                <span className={`font-medium ${!isExists ? 'text-red-600' : 'text-purple-600'}`}>
                    {isExists ? 'EXISTS' : 'NOT EXISTS'}
                </span>
                {subqueryData && (
                    <SubqueryTooltip subquery={subqueryData} onOpenModal={onOpenModal} />
                )}
            </div>
        );
    }

    // InCondition - проверяем по наличию inValues
    if (node.inValues !== undefined && node.inValues.length > 0) {
        const hasSubquery = node.inValues.some((v: any) => v?.subqueryIR || v?.subquery);
        const subqueryItem = node.inValues.find((v: any) => v?.subqueryIR || v?.subquery);

        if (hasSubquery && subqueryItem && onOpenModal) {
            const leftExpr = formatExpression(node.operand);
            return (
                <div className="font-mono text-sm py-2 flex items-center flex-wrap gap-2">
                    <span className="text-blue-600 font-medium">{leftExpr}</span>
                    <span className="text-gray-400">IN</span>
                    <SubqueryTooltip subquery={subqueryItem} onOpenModal={onOpenModal} />
                </div>
            );
        }

        const values = node.inValues.map((v: any) => formatExpression(v)).join(', ');
        const displayValues = values.length > 50 ? values.substring(0, 50) + '...' : values;
        return (
            <div className="font-mono text-sm py-1 flex items-center flex-wrap gap-1">
                <span className="text-blue-600 font-medium">{formatExpression(node.operand)}</span>
                <span className="text-gray-400 mx-1">IN</span>
                <span className="text-gray-600 bg-gray-100 px-2 py-0.5 rounded">({displayValues})</span>
            </div>
        );
    }

    // Comparison (простое сравнение)
    if (node.operand !== undefined && node.value !== undefined && node.operator) {
        // Проверяем, не является ли value подзапросом
        if ((node.value?.subqueryIR || node.value?.subquery) && onOpenModal) {
            return (
                <div className="font-mono text-sm py-2 flex items-center flex-wrap gap-2">
                    <span className="text-blue-600 font-medium">{formatExpression(node.operand)}</span>
                    <span className="text-gray-400 mx-1">{node.operator}</span>
                    <SubqueryTooltip subquery={node.value} onOpenModal={onOpenModal} />
                </div>
            );
        }

        // Проверяем, не является ли operand или value агрегатной функцией
        const isOperandAggregate = node.operand?.type === 'COUNT' || node.operand?.type === 'SUM' ||
            node.operand?.type === 'AVG' || node.operand?.type === 'MIN' ||
            node.operand?.type === 'MAX';
        const isValueAggregate = node.value?.type === 'COUNT' || node.value?.type === 'SUM' ||
            node.value?.type === 'AVG' || node.value?.type === 'MIN' ||
            node.value?.type === 'MAX';

        // Если operand или value - агрегатная функция, форматируем специально
        if (isOperandAggregate || isValueAggregate) {
            const leftExpr = isOperandAggregate ? formatAggregateExpression(node.operand) : formatExpression(node.operand);
            const rightExpr = isValueAggregate ? formatAggregateExpression(node.value) : formatExpression(node.value);

            return (
                <div className="font-mono text-sm py-1 flex items-center flex-wrap gap-1">
                    <span className="text-blue-600 font-medium">{leftExpr}</span>
                    <span className="text-gray-400 mx-1">{node.operator}</span>
                    <span className="text-green-600">{rightExpr}</span>
                </div>
            );
        }

        return (
            <div className="font-mono text-sm py-1 flex items-center flex-wrap gap-1">
                <span className="text-blue-600 font-medium">{formatExpression(node.operand)}</span>
                <span className="text-gray-400 mx-1">{node.operator}</span>
                <span className="text-green-600">{formatExpression(node.value)}</span>
            </div>
        );
    }

    // BetweenCondition
    if (node.start !== undefined && node.end !== undefined) {
        return (
            <div className="font-mono text-sm py-1 flex items-center flex-wrap gap-1">
                <span className="text-blue-600 font-medium">{formatExpression(node.operand)}</span>
                <span className="text-gray-400 mx-1">BETWEEN</span>
                <span className="text-green-600">{node.start}</span>
                <span className="text-gray-400 mx-1">AND</span>
                <span className="text-green-600">{node.end}</span>
            </div>
        );
    }

    // NullCheck
    if (node.isNull !== undefined) {
        return (
            <div className="font-mono text-sm py-1 flex items-center flex-wrap gap-1">
                <span className="text-blue-600 font-medium">{formatExpression(node.operand)}</span>
                <span className="text-gray-400 mx-1">IS {node.isNull ? 'NULL' : 'NOT NULL'}</span>
            </div>
        );
    }

    // Если есть дети, но тип не определён (fallback)
    if (children.length > 0) {
        return (
            <div className="space-y-2">
                {children.map((child: any, idx: number) => (
                    <ConditionNodeRenderer
                        key={idx}
                        node={child}
                        depth={depth}
                        compact={compact}
                        showAll={showAll}
                        onOpenModal={onOpenModal}
                    />
                ))}
            </div>
        );
    }

    return <div className="text-gray-400 text-sm italic">(условие не определено)</div>;
};

const formatExpression = (expr: any): string => {
    if (!expr) return '?';
    if (typeof expr === 'string') return expr;
    if (expr.value !== undefined) return String(expr.value);

    // Обработка агрегатной функции
    if (expr.type === 'COUNT' || expr.type === 'SUM' || expr.type === 'AVG' ||
        expr.type === 'MIN' || expr.type === 'MAX') {
        return formatAggregateExpression(expr);
    }

    // Обработка поля (Field)
    if (expr.field !== undefined) {
        if (typeof expr.field === 'object') {
            const fieldObj = expr.field;
            if (fieldObj.source && fieldObj.field) {
                return `${fieldObj.source}.${fieldObj.field}`;
            }
            return fieldObj.field || fieldObj.toString();
        }
        if (expr.source) {
            return `${expr.source}.${expr.field}`;
        }
        return expr.field;
    }

    // Обработка BinaryOperation
    if (expr.operator && expr.left && expr.right) {
        const opMap: Record<string, string> = {
            'MULTIPLY': '*',
            'ADD': '+',
            'SUBTRACT': '-',
            'DIVIDE': '/',
            'MOD': '%'
        };
        const symbol = opMap[expr.operator] || expr.operator;
        return `${formatExpression(expr.left)} ${symbol} ${formatExpression(expr.right)}`;
    }

    // Обработка Subquery
    if (expr.subqueryIR) return 'подзапрос';
    if (expr.correlations) return 'коррелированный подзапрос';

    return '?';
};

const formatAggregateExpression = (agg: any): string => {
    const distinct = agg.distinct ? 'DISTINCT ' : '';
    let fieldName = '*';

    if (agg.field) {
        if (typeof agg.field === 'object') {
            if (agg.field.field) {
                fieldName = agg.field.source ? `${agg.field.source}.${agg.field.field}` : agg.field.field;
            } else {
                fieldName = agg.field.toString();
            }
        } else if (typeof agg.field === 'string') {
            fieldName = agg.field;
        }
    }

    return `${agg.type}(${distinct}${fieldName})`;
};

export default ConditionNodeRenderer;