import React, { useState } from 'react';
import { Database, X, Filter, Link, Group, SortAsc, Hash, Layers, Code, AlertCircle, TrendingUp } from 'lucide-react';
import ConditionNodeRenderer from './ConditionNodeRenderer';

interface SubqueryModalProps {
    isOpen: boolean;
    onClose: () => void;
    subquery: any;
    title?: string;
}

const SubqueryModal: React.FC<SubqueryModalProps> = ({ isOpen, onClose, subquery, title = 'Детали подзапроса' }) => {
    const [nestedModalSubquery, setNestedModalSubquery] = useState<any>(null);
    const [isNestedModalOpen, setIsNestedModalOpen] = useState(false);

    if (!isOpen || !subquery) return null;

    const subqueryData = subquery.subqueryIR || subquery;

    const openNestedModal = (nestedSubquery: any) => {
        setNestedModalSubquery(nestedSubquery);
        setIsNestedModalOpen(true);
    };

    const closeNestedModal = () => {
        setIsNestedModalOpen(false);
        setNestedModalSubquery(null);
    };

    // Подсчёт подзапросов внутри подзапроса
    const countNestedSubqueries = (node: any): number => {
        if (!node) return 0;
        let count = 0;
        if (node.subquery) count++;
        if (node.subqueryIR) count++;
        if (node.inValues) {
            count += node.inValues.filter((v: any) => v?.subqueryIR || v?.subquery).length;
        }
        if (node.children) {
            for (const child of node.children) {
                count += countNestedSubqueries(child);
            }
        }
        return count;
    };

    const nestedSubqueryCount = countNestedSubqueries(subqueryData.whereCondition);

    // Рекурсивное извлечение вложенного подзапроса
    const getNestedSubquery = (node: any): any => {
        if (!node) return null;
        if (node.subquery) return node.subquery;
        if (node.subqueryIR) return { subqueryIR: node.subqueryIR };
        if (node.children) {
            for (const child of node.children) {
                const found = getNestedSubquery(child);
                if (found) return found;
            }
        }
        return null;
    };

    return (
        <>
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
                <div className="bg-white rounded-lg shadow-xl w-full max-w-4xl max-h-[90vh] flex flex-col m-4">
                    {/* Заголовок */}
                    <div className="flex items-center justify-between p-4 border-b bg-gradient-to-r from-indigo-50 to-purple-50 rounded-t-lg">
                        <div className="flex items-center gap-2">
                            <Database size={20} className="text-indigo-600" />
                            <h2 className="text-lg font-semibold text-gray-800">{title}</h2>
                        </div>
                        <button
                            onClick={onClose}
                            className="text-gray-500 hover:text-gray-700 transition-colors"
                        >
                            <X size={20} />
                        </button>
                    </div>

                    {/* Содержание */}
                    <div className="flex-1 overflow-y-auto p-4 space-y-4">
                        {/* Статистика подзапроса */}
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                            <div className="bg-blue-50 border border-blue-200 rounded-lg p-3">
                                <div className="flex items-center justify-between">
                                    <span className="text-xs font-medium text-blue-700">JOIN</span>
                                    <Link size={14} className="text-blue-600" />
                                </div>
                                <div className="text-2xl font-bold text-blue-700 mt-1">{subqueryData.joins?.length || 0}</div>
                            </div>
                            <div className="bg-green-50 border border-green-200 rounded-lg p-3">
                                <div className="flex items-center justify-between">
                                    <span className="text-xs font-medium text-green-700">Проекции</span>
                                    <Layers size={14} className="text-green-600" />
                                </div>
                                <div className="text-2xl font-bold text-green-700 mt-1">{subqueryData.projectionFields?.length || 0}</div>
                            </div>
                            <div className="bg-orange-50 border border-orange-200 rounded-lg p-3">
                                <div className="flex items-center justify-between">
                                    <span className="text-xs font-medium text-orange-700">GROUP BY</span>
                                    <Group size={14} className="text-orange-600" />
                                </div>
                                <div className="text-2xl font-bold text-orange-700 mt-1">{subqueryData.groupByFields?.length || 0}</div>
                            </div>
                            <div className="bg-purple-50 border border-purple-200 rounded-lg p-3">
                                <div className="flex items-center justify-between">
                                    <span className="text-xs font-medium text-purple-700">Подзапросы</span>
                                    <Code size={14} className="text-purple-600" />
                                </div>
                                <div className="text-2xl font-bold text-purple-700 mt-1">{nestedSubqueryCount || (subqueryData.hasSubqueries ? 'есть' : 0)}</div>
                            </div>
                        </div>

                        {/* Основная коллекция */}
                        <div className="bg-gradient-to-r from-blue-50 to-indigo-50 rounded-lg p-3 border border-blue-200">
                            <div className="flex items-center justify-between">
                                <div className="flex items-center gap-2">
                                    <Database className="text-blue-600" size={16} />
                                    <span className="font-medium text-gray-700">Основная коллекция</span>
                                </div>
                                <code className="text-sm bg-blue-100 px-2 py-1 rounded text-blue-700 font-mono">
                                    {subqueryData.mainCollection || 'не указана'}
                                </code>
                            </div>

                            {/* Флаги подзапроса */}
                            <div className="flex flex-wrap gap-2 mt-2">
                                {subqueryData.distinct && <Badge icon={<Hash size={12} />} text="DISTINCT" color="purple" />}
                                {subqueryData.hasJoins && <Badge icon={<Link size={12} />} text="JOINS" color="blue" />}
                                {subqueryData.hasGroupBy && <Badge icon={<Group size={12} />} text="GROUP BY" color="orange" />}
                                {subqueryData.hasHaving && <Badge icon={<Filter size={12} />} text="HAVING" color="red" />}
                                {subqueryData.hasSubqueries && <Badge icon={<Code size={12} />} text="Подзапросы" color="indigo" />}
                                {subqueryData.hasAggregateFunctions && <Badge icon={<Layers size={12} />} text="Агрегации" color="green" />}
                                {subqueryData.requiresAggregation && <Badge icon={<TrendingUp size={12} />} text="Требует агрегации" color="emerald" />}
                            </div>
                        </div>
                        {/* Проекции */}
                        {subqueryData.projectionFields && subqueryData.projectionFields.length > 0 && (
                            <SectionCard title="Проекции (SELECT)" icon={<Layers size={16} />} color="green">
                                <div className="space-y-1">
                                    {subqueryData.projectionFields.map((proj: any, idx: number) => (
                                        <div key={idx} className="flex items-center justify-between py-1 border-b last:border-0">
                                            <code className="font-mono text-sm">
                                                {proj.source && `${proj.source}.`}{proj.field || (proj.expression ? formatExpression(proj.expression) : 'expression')}
                                            </code>
                                            {proj.alias && <span className="text-xs text-gray-400">as {proj.alias}</span>}
                                        </div>
                                    ))}
                                </div>
                            </SectionCard>
                        )}
                        {/* JOIN операции */}
                        {subqueryData.joins && subqueryData.joins.length > 0 && (
                            <SectionCard title="JOIN операции" icon={<Link size={16} />} color="blue">
                                <div className="space-y-2">
                                    {subqueryData.joins.map((join: any, idx: number) => (
                                        <div key={idx} className="border rounded-md p-2 text-sm">
                                            <div className="font-mono">
                        <span className={`text-xs px-1.5 py-0.5 rounded ${
                            join.type === 'INNER' ? 'bg-blue-100 text-blue-700' :
                                join.type === 'LEFT' ? 'bg-green-100 text-green-700' :
                                    'bg-orange-100 text-orange-700'
                        }`}>
                            {join.type || 'INNER'} JOIN
                        </span>
                                                <span className="ml-2 text-gray-500">#{idx + 1}</span>
                                            </div>
                                            <div className="mt-1 text-gray-600">
                                                {join.left?.value || (join.left?.subqueryIR ? '(подзапрос)' : '?')}
                                                {join.left?.alias && <span className="text-gray-400 ml-1">as {join.left.alias}</span>}
                                                <span className="mx-1">⟷</span>
                                                {join.right?.value || (join.right?.subqueryIR ? '(подзапрос)' : '?')}
                                                {join.right?.alias && <span className="text-gray-400 ml-1">as {join.right.alias}</span>}
                                            </div>
                                            {join.joinCondition && (
                                                <div className="mt-1 text-xs text-gray-500">
                                                    ON: <ConditionNodeRenderer node={join.joinCondition} depth={0} compact />
                                                </div>
                                            )}
                                        </div>
                                    ))}
                                </div>
                            </SectionCard>
                        )}
                        {/* WHERE условие - с кнопкой для вложенного подзапроса */}
                        {subqueryData.whereCondition && (
                            <SectionCard title="WHERE условие" icon={<Filter size={16} />} color="amber">
                                <ConditionNodeRenderer
                                    node={subqueryData.whereCondition}
                                    depth={0}
                                    compact={false}
                                    onOpenModal={openNestedModal}
                                />
                            </SectionCard>
                        )}
                        {/* HAVING условие */}
                        {subqueryData.havingCondition && (
                            <SectionCard title="HAVING условие" icon={<Filter size={16} />} color="red">
                                <ConditionNodeRenderer
                                    node={subqueryData.havingCondition}
                                    depth={0}
                                    compact={false}
                                />
                            </SectionCard>
                        )}
                        {/* GROUP BY */}
                        {subqueryData.groupByFields && subqueryData.groupByFields.length > 0 && (
                            <SectionCard title="GROUP BY" icon={<Group size={16} />} color="orange">
                                <div className="flex flex-wrap gap-2">
                                    {subqueryData.groupByFields.map((field: any, idx: number) => (
                                        <code key={idx} className="bg-gray-100 px-2 py-1 rounded text-sm font-mono">
                                            {field.source ? `${field.source}.${field.field}` : field.field}
                                        </code>
                                    ))}
                                </div>
                            </SectionCard>
                        )}
                        {/* ORDER BY */}
                        {subqueryData.orderBy && subqueryData.orderBy.length > 0 && (
                            <SectionCard title="ORDER BY" icon={<SortAsc size={16} />} color="purple">
                                <div className="flex flex-wrap gap-2">
                                    {subqueryData.orderBy.map((sort: any, idx: number) => (
                                        <code key={idx} className="bg-gray-100 px-2 py-1 rounded text-sm font-mono">
                                            {sort.source ? `${sort.source}.${sort.field}` : sort.field}
                                            <span className={`ml-1 text-xs ${sort.direction === 'ASC' ? 'text-green-600' : 'text-red-600'}`}>
                        {sort.direction}
                    </span>
                                        </code>
                                    ))}
                                </div>
                            </SectionCard>
                        )}
                        {/* LIMIT / OFFSET */}
                        {(subqueryData.limit || subqueryData.offset) && (
                            <SectionCard title="LIMIT / OFFSET" icon={<Hash size={16} />} color="gray">
                                <div className="flex gap-4">
                                    {subqueryData.limit && (
                                        <div className="flex items-center gap-2">
                                            <span className="text-gray-500 text-xs">LIMIT</span>
                                            <code className="bg-gray-100 px-2 py-0.5 rounded font-mono text-sm">{subqueryData.limit}</code>
                                        </div>
                                    )}
                                    {subqueryData.offset && (
                                        <div className="flex items-center gap-2">
                                            <span className="text-gray-500 text-xs">OFFSET</span>
                                            <code className="bg-gray-100 px-2 py-0.5 rounded font-mono text-sm">{subqueryData.offset}</code>
                                        </div>
                                    )}
                                </div>
                            </SectionCard>
                        )}
                        {/* Корреляции */}
                        {subquery.correlations && subquery.correlations.length > 0 && (
                            <SectionCard title="Корреляции" icon={<AlertCircle size={16} />} color="yellow">
                                <ul className="space-y-1">
                                    {subquery.correlations.map((corr: any, idx: number) => {
                                        // Формируем полное имя внешнего поля
                                        const outerField = corr.outerField?.source
                                            ? `${corr.outerField.source}.${corr.outerField.field}`
                                            : corr.outerField?.field || '?';
                                        // Формируем полное имя внутреннего поля (с алиасом, если есть)
                                        const innerField = corr.innerField?.source
                                            ? `${corr.innerField.source}.${corr.innerField.field}`
                                            : corr.innerField?.field || '?';
                                        return (
                                            <li key={idx} className="text-sm font-mono text-yellow-700">
                                                {outerField} {corr.operator || '='} {innerField}
                                            </li>
                                        );
                                    })}
                                </ul>
                            </SectionCard>
                        )}
                    </div>

                    {/* Кнопка закрытия внизу */}
                    <div className="flex justify-end p-4 border-t bg-gray-50 rounded-b-lg">
                        <button
                            onClick={onClose}
                            className="px-4 py-2 bg-gray-200 text-gray-700 rounded-md hover:bg-gray-300 transition-colors"
                        >
                            Закрыть
                        </button>
                    </div>
                </div>
            </div>

            {/* Вложенное модальное окно для подзапроса внутри подзапроса */}
            <SubqueryModal
                isOpen={isNestedModalOpen}
                onClose={closeNestedModal}
                subquery={nestedModalSubquery}
                title="Детали вложенного подзапроса"
            />
        </>
    );
};

// Вспомогательные компоненты (Badge, SectionCard, formatExpression остаются без изменений)
const Badge: React.FC<{ icon: React.ReactNode; text: string; color: string }> = ({ icon, text, color }) => {
    const colors: Record<string, string> = {
        purple: 'bg-purple-100 text-purple-700',
        blue: 'bg-blue-100 text-blue-700',
        orange: 'bg-orange-100 text-orange-700',
        red: 'bg-red-100 text-red-700',
        green: 'bg-green-100 text-green-700',
        indigo: 'bg-indigo-100 text-indigo-700',
        yellow: 'bg-yellow-100 text-yellow-800',
        emerald: 'bg-emerald-100 text-emerald-700',
        gray: 'bg-gray-100 text-gray-700',
    };
    return (
        <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${colors[color]}`}>
            {icon}{text}
        </span>
    );
};

const SectionCard: React.FC<{ title: string; icon: React.ReactNode; color: string; children: React.ReactNode }> = ({ title, icon, color, children }) => {
    const headers: Record<string, string> = {
        green: 'bg-green-50 text-green-700',
        blue: 'bg-blue-50 text-blue-700',
        amber: 'bg-amber-50 text-amber-700',
        orange: 'bg-orange-50 text-orange-700',
        purple: 'bg-purple-50 text-purple-700',
        red: 'bg-red-50 text-red-700',
        gray: 'bg-gray-50 text-gray-700',
        yellow: 'bg-yellow-50 text-yellow-700',
    };
    return (
        <div className="border rounded-lg overflow-hidden">
            <div className={`px-3 py-2 flex items-center gap-2 ${headers[color]}`}>
                {icon}
                <h4 className="font-medium text-sm">{title}</h4>
            </div>
            <div className="p-3">{children}</div>
        </div>
    );
};

const formatExpression = (expr: any): string => {
    if (!expr) return '?';
    if (typeof expr === 'string') return expr;
    if (expr.value !== undefined) return String(expr.value);

    // Обработка поля (Field)
    if (expr.field !== undefined && expr.source !== undefined) {
        return expr.source ? `${expr.source}.${expr.field}` : expr.field;
    }
    if (expr.field !== undefined) {
        if (typeof expr.field === 'object') {
            // Вложенный field объект (например, в Aggregate)
            const fieldObj = expr.field;
            if (fieldObj.source && fieldObj.field) {
                return `${fieldObj.source}.${fieldObj.field}`;
            }
            return fieldObj.field || fieldObj.toString();
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

    // Обработка Aggregate (COUNT, SUM, AVG, MIN, MAX)
    if (expr.type === 'COUNT' || expr.type === 'SUM' || expr.type === 'AVG' ||
        expr.type === 'MIN' || expr.type === 'MAX') {
        const distinct = expr.distinct ? 'DISTINCT ' : '';
        let fieldName = '*';

        if (expr.field) {
            if (typeof expr.field === 'object') {
                if (expr.field.field) {
                    fieldName = expr.field.source ? `${expr.field.source}.${expr.field.field}` : expr.field.field;
                } else {
                    fieldName = expr.field.toString();
                }
            } else if (typeof expr.field === 'string') {
                fieldName = expr.field;
            }
        }

        return `${expr.type}(${distinct}${fieldName})`;
    }

    // Обработка Subquery
    if (expr.subqueryIR) return 'подзапрос';
    if (expr.correlations) return 'коррелированный подзапрос';

    // Если ничего не подошло
    return '?';
};

export default SubqueryModal;