import { useState } from 'react';
import { Code2, Database, FileSearch, Maximize2, Minimize2, Braces } from 'lucide-react';
import SyntaxTree from './SyntaxTree';
import {AnalysisResult} from "./types.ts";

function App() {
    const [sqlQuery, setSqlQuery] = useState('');
    const [activeTab, setActiveTab] = useState<'lexical' | 'syntax' | 'code' | null>(null);
    const [analysisResult, setAnalysisResult] = useState<AnalysisResult | null>(null);
    const [mongoCode, setMongoCode] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [isTreeFullscreen, setIsTreeFullscreen] = useState(false);

    const analyseSql = async () => {
        setActiveTab(null);
        setError(null);
        setMongoCode(null);
        try {
            const response = await fetch('http://localhost:8080/api/analyse', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ sqlQuery }),
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(errorText);
            }

            const result = await response.json();
            setAnalysisResult(result);
            setError(null);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Произошла ошибка');
            setAnalysisResult(null);
        }
    };

    const generateMongoCode = async () => {
        setActiveTab('code');
        setError(null);
        try {
            const response = await fetch('http://localhost:8080/api/generate', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ sqlQuery }),
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(errorText);
            }

            const result = await response.text();
            setMongoCode(result);
            setError(null);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Произошла ошибка');
            setMongoCode(null);
        }
    };

    const copyToClipboard = () => {
        if (mongoCode) {
            navigator.clipboard.writeText(mongoCode);
        }
    };

    return (
        <div className="min-h-screen bg-gray-50">
            <div className="mx-auto p-6">
                <div className="text-center mb-8">
                    <h1 className="text-3xl font-bold text-gray-800 mb-2">Транслятор с SQL в MongoDB</h1>
                    <p className="text-gray-600">Анализируйте и преобразуйте SQL-запросы в формат MongoDB</p>
                </div>

                <div className="flex gap-6 h-[calc(100vh-180px)] w-full">
                    {/* Левый блок ввода */}
                    <div className={`bg-white rounded-lg shadow-md overflow-hidden ${(activeTab || error) && !isTreeFullscreen ? 'w-1/2' : 'w-full'} transition-all duration-300 ${isTreeFullscreen ? 'hidden' : ''}`}>
                        <div className="p-6 border-b border-gray-200 flex gap-3">
                            <button
                                onClick={analyseSql}
                                className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"
                            >
                                <FileSearch size={20} />
                                Анализировать
                            </button>
                            <button
                                onClick={() => analysisResult && setActiveTab('lexical')}
                                className={`flex items-center gap-2 px-4 py-2 rounded-md transition-colors ${
                                    analysisResult && activeTab === 'lexical'
                                        ? 'bg-green-600 text-white'
                                        : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                                }`}
                                disabled={!analysisResult}
                            >
                                <Code2 size={20} />
                                Лексический анализ
                            </button>
                            <button
                                onClick={() => analysisResult && setActiveTab('syntax')}
                                className={`flex items-center gap-2 px-4 py-2 rounded-md transition-colors ${
                                    analysisResult && activeTab === 'syntax'
                                        ? 'bg-purple-600 text-white'
                                        : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                                }`}
                                disabled={!analysisResult}
                            >
                                <Database size={20} />
                                Синтаксический анализ
                            </button>
                            <button
                                onClick={generateMongoCode}
                                className={`flex items-center gap-2 px-4 py-2 rounded-md transition-colors ${
                                    activeTab === 'code'
                                        ? 'bg-amber-600 text-white'
                                        : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                                }`}
                            >
                                <Braces size={20} />
                                Генерация кода
                            </button>
                        </div>
                        <div className="h-[calc(100%-68px)] overflow-auto p-6">
              <textarea
                  id="sqlQuery"
                  value={sqlQuery}
                  onChange={(e) => setSqlQuery(e.target.value)}
                  className="w-full h-full min-h-[300px] px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono text-sm"
                  placeholder="Введите SQL-запрос здесь..."
              />
                        </div>
                    </div>

                    {/* Правый блок результатов или ошибок */}
                    {(activeTab || error) && (
                        <div className={`bg-white rounded-lg shadow-md overflow-hidden flex flex-col ${isTreeFullscreen ? 'fixed inset-0 z-50 m-0' : 'w-1/2'}`}>
                            <div className="p-6 border-b border-gray-200 flex justify-between items-center">
                                <h2 className="text-lg font-semibold text-gray-800">
                                    {error ? 'Ошибка' :
                                        activeTab === 'lexical' ? 'Лексический анализ' :
                                            activeTab === 'syntax' ? 'Синтаксический анализ' :
                                                'Сгенерированный MongoDB код'}
                                </h2>
                                <div className="flex gap-2">
                                    {activeTab === 'code' && mongoCode && (
                                        <button
                                            onClick={copyToClipboard}
                                            className="text-gray-500 hover:text-gray-700 p-1"
                                            title="Копировать в буфер"
                                        >
                                            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                                                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                                            </svg>
                                        </button>
                                    )}
                                    {activeTab === 'syntax' && (
                                        <button
                                            onClick={() => setIsTreeFullscreen(!isTreeFullscreen)}
                                            className="text-gray-500 hover:text-gray-700 p-1"
                                            title={isTreeFullscreen ? 'Свернуть' : 'Развернуть'}
                                        >
                                            {isTreeFullscreen ? <Minimize2 size={20} /> : <Maximize2 size={20} />}
                                        </button>
                                    )}
                                    <button
                                        onClick={() => {
                                            setActiveTab(null);
                                            setError(null);
                                            setIsTreeFullscreen(false);
                                        }}
                                        className="text-gray-500 hover:text-gray-700"
                                    >
                                        ×
                                    </button>
                                </div>
                            </div>
                            <div className="flex-1 overflow-auto p-6">
                                {error && (
                                    <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md mb-6">
                                        {error}
                                    </div>
                                )}

                                {activeTab === 'lexical' && analysisResult && (
                                    <div className="overflow-x-auto min-w-[600px]">
                                        <table className="w-full text-left">
                                            <thead className="bg-gray-50">
                                            <tr>
                                                <th className="px-4 py-2 sticky left-0 bg-white">№</th>
                                                <th className="px-4 py-2 min-w-[200px]">Лексема</th>
                                                <th className="px-4 py-2 min-w-[200px]">Категория</th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            {analysisResult.lexicalResult.map((token, index) => (
                                                <tr key={index} className="border-t">
                                                    <td className="px-4 py-2 sticky left-0 bg-white">{index + 1}</td>
                                                    <td className="px-4 py-2 font-mono whitespace-nowrap">{token.lexeme}</td>
                                                    <td className="px-4 py-2 whitespace-nowrap">{token.category}</td>
                                                </tr>
                                            ))}
                                            </tbody>
                                        </table>
                                    </div>
                                )}

                                {activeTab === 'syntax' && analysisResult && (
                                    <SyntaxTree data={analysisResult.syntaxResult} isFullscreen={isTreeFullscreen} />
                                )}

                                {activeTab === 'code' && mongoCode && (
                                    <div className="bg-gray-900 rounded-lg p-4 font-mono text-sm">
                          <pre className="text-green-400 overflow-x-auto whitespace-pre-wrap">
                            {mongoCode}
                          </pre>
                                    </div>
                                )}
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

export default App;