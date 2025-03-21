import React from 'react';
import { Tree } from 'react-d3-tree';

interface SyntaxTreeProps {
    data: any;
}

interface TreeNodeDatum {
    name: string;
    attributes?: {
        [key: string]: string | number | boolean;
    };
    children?: TreeNodeDatum[];
}

const SyntaxTree: React.FC<SyntaxTreeProps> = ({ data }) => {
    // Преобразуем данные в формат, понятный react-d3-tree
    const convertToTreeData = (node: any): TreeNodeDatum | null => {
        if (!node) return null;

        // Если узел — лист, используем только лексему
        if (node.children.length === 0) {
            return {
                name: `${node.token.lexeme}`, // Отображаем только лексему
                attributes: {}, // Листья не имеют атрибутов
                children: [], // Лист не имеет детей
            };
        }

        // Если узел имеет детей, добавляем тип узла как атрибут
        return {
            name: '', // Имя узла не отображается
            attributes: {
                type: node.nodeType, // Тип узла
            },
            children: node.children.map((child: any) => convertToTreeData(child)),
        };
    };

    const treeData: TreeNodeDatum | null = convertToTreeData(data);

    return (
        <div className="w-full h-[600px] overflow-auto">
            {treeData ? (
                <Tree
                    data={treeData}
                    orientation="vertical" // Вертикальное отображение
                    translate={{ x: 400, y: 50 }} // Центрирование дерева
                    pathFunc="step" // Тип линий (прямые или ступенчатые)
                    nodeSize={{ x: 200, y: 100 }} // Размер узлов
                    separation={{ siblings: 1, nonSiblings: 2 }} // Расстояние между узлами
                    renderCustomNodeElement={({ nodeDatum, toggleNode }) => (
                        <g>
                            <circle
                                r={15}
                                fill={nodeDatum.children ? '#4F46E5' : '#FEF3C7'} // Цвет узлов: синий для промежуточных, жёлтый для листьев
                                stroke="#3730A3" // Цвет границ узлов
                                strokeWidth={2}
                                onClick={toggleNode}
                            />
                            <text
                                x={20}
                                y={5}
                                fill="#1E293B" // Цвет текста
                                fontSize="14px"
                                textAnchor="start"
                            >
                                {nodeDatum.name} {/* Отображаем имя узла (лексему для листьев) */}
                            </text>
                            {nodeDatum.attributes?.type && (
                                <text
                                    x={20}
                                    y={25}
                                    fill="#6B7280" // Цвет дополнительного текста
                                    fontSize="12px"
                                    textAnchor="start"
                                >
                                    {nodeDatum.attributes.type} {/* Отображаем тип узла для промежуточных узлов */}
                                </text>
                            )}
                        </g>
                    )}
                />
            ) : (
                <p className="text-gray-500">No syntax tree data available.</p>
            )}
        </div>
    );
};

export default SyntaxTree;