import React, { useState } from 'react';
import {
    Database,
    Link,
    Filter,
    Group,
    SortAsc,
    Hash,
    Layers,
    Code,
    AlertCircle,
    CheckCircle,
    ChevronRight,
    ChevronDown,
    ArrowLeftRight,
    ArrowRightLeft,
    Eye,
    EyeOff
} from 'lucide-react';
import SubqueryModal from './SubqueryModal';
import ConditionNodeRenderer from './ConditionNodeRenderer';

interface IRViewerProps {
    ir: any;
}

const IRViewer: React.FC<IRViewerProps> = ({ ir }) => {
    const [expandedJoins, setExpandedJoins] = useState<Set<number>>(new Set());
    const [showAllConditions, setShowAllConditions] = useState(true);
    const [modalSubquery, setModalSubquery] = useState<any>(null);
    const [isModalOpen, setIsModalOpen] = useState(false);

    const toggleJoin = (index: number) => {
        const newExpanded = new Set(expandedJoins);
        if (newExpanded.has(index)) {
            newExpanded.delete(index);
        } else {
            newExpanded.add(index);
        }
        setExpandedJoins(newExpanded);
    };

    const openSubqueryModal = (subquery: any) => {
        setModalSubquery(subquery);
        setIsModalOpen(true);
    };

    // Рекурсивный подсчёт подзапросов в любом месте
    const countAllSubqueries = (node: any): number => {
        if (!node) return 0;
        let count = 0;

        // Проверка на наличие подзапроса в различных полях
        if (node.subquery) count++;
        if (node.subqueryIR) count++;
        if (node.inValues) {
            count += node.inValues.filter((v: any) => v?.subqueryIR || v?.subquery).length;
        }

        // Рекурсивный обход
        if (node.children) {
            for (const child of node.children) {
                count += countAllSubqueries(child);
            }
        }
        if (node.operand) count += countAllSubqueries(node.operand);
        if (node.value) count += countAllSubqueries(node.value);
        if (node.left) count += countAllSubqueries(node.left);
        if (node.right) count += countAllSubqueries(node.right);

        return count;
    };

    // Обновлённый getSubqueryCount
    const getSubqueryCount = () => {
        let count = 0;

        // Подзапросы в проекциях
        if (ir.projectionFields) {
            count += ir.projectionFields.filter((p: any) => p.subqueryIR).length;
        }

        // Подзапросы в JOIN
        if (ir.joins) {
            count += ir.joins.filter((j: any) => j.right?.subqueryIR).length;
        }

        // Подзапросы в WHERE и HAVING
        if (ir.whereCondition) {
            count += countAllSubqueries(ir.whereCondition);
        }
        if (ir.havingCondition) {
            count += countAllSubqueries(ir.havingCondition);
        }

        return count > 0 ? count : (ir.hasSubqueries ? 'есть' : 0);
    };

    const countSubqueriesInNode = (node: any): number => {
        if (!node) return 0;
        let count = 0;
        if (node.inValues?.some((v: any) => v?.subqueryIR)) count++;
        if (node.subquery) count++;
        if (node.children) {
            for (const child of node.children) {
                count += countSubqueriesInNode(child);
            }
        }
        return count;
    };

    return (
        <>
            <div className="space-y-4 font-sans">
                {/* Заголовок с основной информацией */}
                <div className="bg-gradient-to-r from-blue-50 to-indigo-50 rounded-lg p-4 border border-blue-200">
                    <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center gap-2">
                            <Database className="text-blue-600" size={20} />
                            <h3 className="font-semibold text-gray-800">Основная коллекция</h3>
                        </div>
                        <code className="text-sm bg-blue-100 px-3 py-1 rounded-full text-blue-700 font-mono">
                            {ir.mainCollection || 'не указана'}
                        </code>
                    </div>

                    {/* Флаги */}
                    <div className="flex flex-wrap gap-2 mb-4">
                        {ir.distinct && <Badge icon={<Hash size={14} />} text="DISTINCT" color="purple" />}
                        {ir.hasJoins && <Badge icon={<Link size={14} />} text="JOINS" color="blue" />}
                        {ir.hasGroupBy && <Badge icon={<Group size={14} />} text="GROUP BY" color="orange" />}
                        {ir.hasHaving && <Badge icon={<Filter size={14} />} text="HAVING" color="red" />}
                        {ir.hasSubqueries && <Badge icon={<Code size={14} />} text="Подзапросы" color="indigo" />}
                        {ir.hasAggregateFunctions && <Badge icon={<Layers size={14} />} text="Агрегации" color="green" />}
                        {ir.hasCorrelatedSubqueries && <Badge icon={<AlertCircle size={14} />} text="Коррелированные" color="yellow" />}
                        {ir.requiresAggregation && <Badge icon={<CheckCircle size={14} />} text="Требует агрегации" color="emerald" />}
                    </div>

                    {/* Статистика */}
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                        <StatCard label="JOIN" value={ir.joins?.length || 0} icon={<Link size={16} />} color="blue" />
                        <StatCard label="Проекции" value={ir.projectionFields?.length || 0} icon={<Layers size={16} />} color="green" />
                        <StatCard label="GROUP BY" value={ir.groupByFields?.length || 0} icon={<Group size={16} />} color="orange" />
                        <StatCard label="Подзапросы" value={getSubqueryCount()} icon={<Code size={16} />} color="purple" />
                    </div>
                </div>

                {/* Проекции (SELECT) */}
                {ir.projectionFields && ir.projectionFields.length > 0 && (
                    <SectionCard title="Проекции (SELECT)" icon={<Layers size={18} />} color="green">
                        <div className="space-y-2">
                            {ir.projectionFields.map((field: any, idx: number) => (
                                <ProjectionItem key={idx} projection={field} />
                            ))}
                        </div>
                    </SectionCard>
                )}

                {/* JOINs */}
                {ir.joins && ir.joins.length > 0 && (
                    <SectionCard title="JOIN операции" icon={<Link size={18} />} color="blue">
                        <div className="space-y-3">
                            {ir.joins.map((join: any, idx: number) => {
                                const actualIndex = ir.joins.length - 1 - idx;
                                const isExpanded = expandedJoins.has(actualIndex);
                                return (
                                    <JoinItem
                                        key={actualIndex}
                                        join={join}
                                        index={actualIndex}
                                        isExpanded={isExpanded}
                                        onToggle={() => toggleJoin(actualIndex)}
                                        onOpenModal={openSubqueryModal}
                                    />
                                );
                            })}
                        </div>
                    </SectionCard>
                )}

                {/* WHERE условие */}
                {ir.whereCondition && (
                    <SectionCard
                        title="WHERE условие"
                        icon={<Filter size={18} />}
                        color="amber"
                        action={
                            <button
                                onClick={() => setShowAllConditions(!showAllConditions)}
                                className="text-xs text-gray-500 hover:text-gray-700 flex items-center gap-1"
                            >
                                {showAllConditions ? <EyeOff size={14} /> : <Eye size={14} />}
                                {showAllConditions ? 'свернуть' : 'развернуть'}
                            </button>
                        }
                    >
                        <ConditionNodeRenderer
                            node={ir.whereCondition}
                            depth={0}
                            compact={false}
                            showAll={showAllConditions}
                            onOpenModal={openSubqueryModal}
                        />
                    </SectionCard>
                )}

                {/* GROUP BY */}
                {ir.groupByFields && ir.groupByFields.length > 0 && (
                    <SectionCard title="GROUP BY" icon={<Group size={18} />} color="orange">
                        <div className="flex flex-wrap gap-2">
                            {ir.groupByFields.map((field: any, idx: number) => (
                                <code key={idx} className="bg-gray-100 px-3 py-1 rounded-full text-sm font-mono">
                                    {field.source ? `${field.source}.${field.field}` : field.field}
                                </code>
                            ))}
                        </div>
                    </SectionCard>
                )}

                {/* ORDER BY */}
                {ir.orderBy && ir.orderBy.length > 0 && (
                    <SectionCard title="ORDER BY" icon={<SortAsc size={18} />} color="purple">
                        <div className="flex flex-wrap gap-2">
                            {ir.orderBy.map((sort: any, idx: number) => (
                                <code key={idx} className="bg-gray-100 px-3 py-1 rounded-full text-sm font-mono">
                                    {sort.source ? `${sort.source}.${sort.field}` : sort.field}
                                    <span className={`ml-2 px-1.5 py-0.5 rounded text-xs ${
                                        sort.direction === 'ASC' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
                                    }`}>
                                        {sort.direction}
                                    </span>
                                </code>
                            ))}
                        </div>
                    </SectionCard>
                )}

                {/* HAVING условие */}
                {ir.havingCondition && (
                    <SectionCard title="HAVING условие" icon={<Filter size={18} />} color="red">
                        <ConditionNodeRenderer
                            node={ir.havingCondition}
                            depth={0}
                            compact={false}
                            showAll={showAllConditions}
                            onOpenModal={openSubqueryModal}
                        />
                    </SectionCard>
                )}

                {/* Limit / Offset */}
                {(ir.limit || ir.offset) && (
                    <SectionCard title="Пагинация" icon={<Hash size={18} />} color="gray">
                        <div className="flex gap-4">
                            {ir.limit && (
                                <div className="flex items-center gap-2">
                                    <span className="text-gray-500">LIMIT:</span>
                                    <code className="bg-gray-100 px-2 py-1 rounded font-mono">{ir.limit}</code>
                                </div>
                            )}
                            {ir.offset && (
                                <div className="flex items-center gap-2">
                                    <span className="text-gray-500">OFFSET:</span>
                                    <code className="bg-gray-100 px-2 py-1 rounded font-mono">{ir.offset}</code>
                                </div>
                            )}
                        </div>
                    </SectionCard>
                )}
            </div>

            {/* Модальное окно для подзапроса */}
            <SubqueryModal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                subquery={modalSubquery}
                title="Детали подзапроса"
            />
        </>
    );
};

// ==================== Вспомогательные компоненты ====================

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
        <span className={`inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium ${colors[color]}`}>
            {icon}
            {text}
        </span>
    );
};

const StatCard: React.FC<{ label: string; value: string | number; icon: React.ReactNode; color: string }> = ({ label, value, icon, color }) => {
    const colors: Record<string, string> = {
        blue: 'bg-blue-50 border-blue-200 text-blue-700',
        green: 'bg-green-50 border-green-200 text-green-700',
        orange: 'bg-orange-50 border-orange-200 text-orange-700',
        purple: 'bg-purple-50 border-purple-200 text-purple-700',
    };

    return (
        <div className={`rounded-lg border p-3 ${colors[color]}`}>
            <div className="flex items-center justify-between">
                <span className="text-xs font-medium">{label}</span>
                {icon}
            </div>
            <div className="text-2xl font-bold mt-1">{value}</div>
        </div>
    );
};

const SectionCard: React.FC<{
    title: string;
    icon: React.ReactNode;
    color: string;
    children: React.ReactNode;
    action?: React.ReactNode
}> = ({ title, icon, color, children, action }) => {
    const borders: Record<string, string> = {
        green: 'border-green-200',
        blue: 'border-blue-200',
        amber: 'border-amber-200',
        orange: 'border-orange-200',
        purple: 'border-purple-200',
        red: 'border-red-200',
        gray: 'border-gray-200',
    };

    const headers: Record<string, string> = {
        green: 'bg-green-50 text-green-700',
        blue: 'bg-blue-50 text-blue-700',
        amber: 'bg-amber-50 text-amber-700',
        orange: 'bg-orange-50 text-orange-700',
        purple: 'bg-purple-50 text-purple-700',
        red: 'bg-red-50 text-red-700',
        gray: 'bg-gray-50 text-gray-700',
    };

    return (
        <div className={`border rounded-lg overflow-hidden ${borders[color]}`}>
            <div className={`px-4 py-2 flex items-center justify-between ${headers[color]}`}>
                <div className="flex items-center gap-2">
                    {icon}
                    <h4 className="font-medium">{title}</h4>
                </div>
                {action}
            </div>
            <div className="p-4">
                {children}
            </div>
        </div>
    );
};

const ProjectionItem: React.FC<{ projection: any }> = ({ projection }) => {
    const getProjectionType = () => {
        if (projection.type === 'COUNT' || projection.type === 'SUM' || projection.type === 'AVG' ||
            projection.type === 'MIN' || projection.type === 'MAX') return 'aggregate';
        if (projection.expression) return 'arithmetic';
        if (projection.subqueryIR) return 'subquery';
        if (projection.field === '*') return 'all';
        return 'field';
    };

    const type = getProjectionType();

    const icons: Record<string, React.ReactNode> = {
        aggregate: <Layers size={14} />,
        arithmetic: <Code size={14} />,
        subquery: <Database size={14} />,
        all: <Hash size={14} />,
        field: <Code size={14} />,
    };

    const colors: Record<string, string> = {
        aggregate: 'text-green-600 bg-green-50',
        arithmetic: 'text-purple-600 bg-purple-50',
        subquery: 'text-indigo-600 bg-indigo-50',
        all: 'text-gray-600 bg-gray-50',
        field: 'text-blue-600 bg-blue-50',
    };

    let displayValue = '';
    if (projection.source) displayValue += `${projection.source}.`;
    if (projection.field) displayValue += projection.field;
    else if (projection.type) displayValue += `${projection.type}(${projection.field?.field || '*'})`;
    else if (projection.expression) {
        if (projection.expression.operator) {
            displayValue = formatExpression(projection.expression);
        } else {
            displayValue = 'выражение';
        }
    } else if (projection.subqueryIR) displayValue = 'подзапрос';
    else displayValue = '?';

    return (
        <div className="flex items-center justify-between py-2 border-b last:border-0 hover:bg-gray-50 -mx-2 px-2 rounded transition-colors">
            <div className="flex items-center gap-3">
                <span className={`p-1 rounded ${colors[type]}`}>
                    {icons[type]}
                </span>
                <code className="font-mono text-sm">
                    {displayValue}
                </code>
            </div>
            {projection.alias && (
                <span className="text-xs text-gray-400">as {projection.alias}</span>
            )}
        </div>
    );
};

const JoinItem: React.FC<{
    join: any;
    index: number;
    isExpanded: boolean;
    onToggle: () => void;
    onOpenModal: (subquery: any) => void;
}> = ({ join, index, isExpanded, onToggle, onOpenModal }) => {
    const getJoinTypeColor = () => {
        switch (join.type) {
            case 'INNER': return 'border-blue-200 bg-blue-50';
            case 'LEFT': return 'border-green-200 bg-green-50';
            case 'RIGHT': return 'border-orange-200 bg-orange-50';
            default: return 'border-gray-200 bg-gray-50';
        }
    };

    const getJoinTypeTextColor = () => {
        switch (join.type) {
            case 'INNER': return 'text-blue-700 bg-blue-100';
            case 'LEFT': return 'text-green-700 bg-green-100';
            case 'RIGHT': return 'text-orange-700 bg-orange-100';
            default: return 'text-gray-700 bg-gray-100';
        }
    };

    const getJoinIcon = () => {
        switch (join.type) {
            case 'INNER': return <Link size={14} />;
            case 'LEFT': return <ArrowLeftRight size={14} />;
            case 'RIGHT': return <ArrowRightLeft size={14} />;
            default: return <Link size={14} />;
        }
    };

    const getLeftLabel = () => {
        if (join.left?.value) return join.left.value;
        if (join.left?.subqueryIR) return '(подзапрос)';
        return '?';
    };

    const getRightLabel = () => {
        if (join.right?.value) return join.right.value;
        if (join.right?.subqueryIR) return '(подзапрос)';
        return '?';
    };

    const hasCorrelations = join.right?.subqueryIR?.hasCorrelatedSubqueries ||
        join.right?.correlations?.length > 0;

    return (
        <div className={`border rounded-lg overflow-hidden ${getJoinTypeColor()}`}>
            <div
                className="px-4 py-3 flex items-center justify-between cursor-pointer hover:bg-opacity-80 transition-colors"
                onClick={onToggle}
            >
                <div className="flex items-center gap-3">
                    {isExpanded ? <ChevronDown size={18} className="text-gray-500" /> : <ChevronRight size={18} className="text-gray-500" />}
                    <span className={`text-xs font-mono px-2 py-1 rounded flex items-center gap-1 ${getJoinTypeTextColor()}`}>
                        {getJoinIcon()}
                        {join.type || 'INNER'} JOIN
                    </span>
                    <span className="text-gray-400 text-sm">#{index + 1}</span>
                    <div className="flex items-center gap-2 text-sm">
                        <code className="font-mono bg-white px-2 py-0.5 rounded border text-gray-700">
                            {getLeftLabel()}
                            {join.left?.alias && <span className="text-gray-400 ml-1">as {join.left.alias}</span>}
                        </code>
                        <span className="text-gray-400 text-xs">⟷</span>
                        <code className="font-mono bg-white px-2 py-0.5 rounded border text-gray-700">
                            {getRightLabel()}
                            {join.right?.alias && <span className="text-gray-400 ml-1">as {join.right.alias}</span>}
                        </code>
                        {hasCorrelations && (
                            <span className="text-xs bg-yellow-100 text-yellow-700 px-1.5 py-0.5 rounded-full">
                                коррелированный
                            </span>
                        )}
                    </div>
                </div>
            </div>

            {isExpanded && (
                <div className="px-4 py-3 border-t border-gray-200 bg-white space-y-3">
                    {join.joinCondition && (
                        <div>
                            <div className="text-xs text-gray-500 mb-2 flex items-center gap-1">
                                <Filter size={12} />
                                ON условие
                            </div>
                            <ConditionNodeRenderer
                                node={join.joinCondition}
                                depth={0}
                                compact
                                onOpenModal={onOpenModal}
                            />
                        </div>
                    )}

                    {join.right?.subqueryIR && (
                        <div>
                            <div className="text-xs text-gray-500 mb-2 flex items-center gap-1">
                                <Database size={12} />
                                Подзапрос в JOIN
                            </div>
                            <div className="bg-gray-50 rounded p-2 text-xs font-mono text-gray-600">
                                {join.right.subqueryIR.mainCollection || 'запрос'}
                                {join.right.correlations?.length > 0 && (
                                    <div className="mt-1 text-yellow-600">
                                        Корреляции: {join.right.correlations.length}
                                    </div>
                                )}
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

const formatExpression = (expr: any): string => {
    if (!expr) return '?';
    if (typeof expr === 'string') return expr;
    if (expr.value !== undefined) return String(expr.value);
    if (expr.field) return expr.source ? `${expr.source}.${expr.field}` : expr.field;
    if (expr.operator) {
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
    if (expr.type === 'COUNT' || expr.type === 'SUM' || expr.type === 'AVG' ||
        expr.type === 'MIN' || expr.type === 'MAX') {
        const distinct = expr.distinct ? 'DISTINCT ' : '';
        const fieldName = expr.field?.field || expr.field || '*';
        return `${expr.type}(${distinct}${fieldName})`;
    }
    if (expr.subqueryIR) return 'подзапрос';
    if (expr.correlations) return 'коррелированный подзапрос';
    return '?';
};

export default IRViewer;