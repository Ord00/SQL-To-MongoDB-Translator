import React from 'react';
import { Tree } from 'react-d3-tree';

interface SyntaxTreeProps {
    data: any;
    isFullscreen: boolean;
}

interface TreeNodeDatum {
    name: string;
    attributes?: {
        [key: string]: string | number | boolean;
    };
    children?: TreeNodeDatum[];
}

const SyntaxTree: React.FC<SyntaxTreeProps> = ({ data, isFullscreen }) => {
    const convertToTreeData = (node: any): TreeNodeDatum | null => {
        if (!node) return null;

        if (node.children.length === 0) {
            return {
                name: `${node.token.lexeme}`,
                attributes: {},
                children: [],
            };
        }

        return {
            name: '',
            attributes: {
                type: node.nodeType,
            },
            children: node.children.map((child: any) => convertToTreeData(child)),
        };
    };

    const treeData: TreeNodeDatum | null = convertToTreeData(data);

    return (
        <div className={`w-full ${isFullscreen ? 'h-[calc(100vh-120px)]' : 'h-[600px]'} overflow-auto`}>
            {treeData ? (
                <Tree
                    data={treeData}
                    orientation="vertical"
                    translate={{ x: isFullscreen ? window.innerWidth / 2 : 400, y: 50 }}
                    pathFunc="step"
                    nodeSize={{ x: 200, y: 100 }}
                    separation={{ siblings: 1, nonSiblings: 2 }}
                    renderCustomNodeElement={({ nodeDatum, toggleNode }) => (
                        <g>
                            <circle
                                r={15}
                                fill={nodeDatum.children ? '#4F46E5' : '#FEF3C7'}
                                stroke="#3730A3"
                                strokeWidth={2}
                                onClick={toggleNode}
                            />
                            <text
                                x={20}
                                y={5}
                                fill="#1E293B"
                                fontSize="14px"
                                textAnchor="start"
                            >
                                {nodeDatum.name}
                            </text>
                            {nodeDatum.attributes?.type && (
                                <text
                                    x={20}
                                    y={25}
                                    fill="#6B7280"
                                    fontSize="12px"
                                    textAnchor="start"
                                >
                                    {nodeDatum.attributes.type}
                                </text>
                            )}
                        </g>
                    )}
                />
            ) : (
                <p className="text-gray-500">Нет данных для отображения синтаксического дерева</p>
            )}
        </div>
    );
};

export default SyntaxTree;