// SubqueryTooltip.tsx
import React from 'react';
import { Database } from 'lucide-react';

interface SubqueryTooltipProps {
    subquery: any;
    onOpenModal: (subquery: any) => void;
}

const SubqueryTooltip: React.FC<SubqueryTooltipProps> = ({ subquery, onOpenModal }) => {
    if (!subquery) return <span className="text-gray-400 text-xs">(подзапрос)</span>;

    const subqueryData = subquery.subqueryIR || subquery;
    const hasCorrelations = subquery.correlations?.length > 0;

    return (
        <button
            onClick={() => onOpenModal(subquery)}
            className="text-xs bg-indigo-100 text-indigo-700 px-2 py-0.5 rounded-full hover:bg-indigo-200 transition-colors flex items-center gap-1 cursor-pointer"
        >
            <Database size={10} />
            подзапрос
            {hasCorrelations && <span className="ml-1 text-yellow-600">⚡</span>}
            {subqueryData.whereCondition && <span className="ml-1 text-purple-600">↺</span>}
        </button>
    );
};

export default SubqueryTooltip;