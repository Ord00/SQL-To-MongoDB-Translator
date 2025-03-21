import { useState } from 'react';
import { Code2, Database, FileSearch } from 'lucide-react';
import SyntaxTree from './SyntaxTree';

function App() {
  const [sqlQuery, setSqlQuery] = useState('');
  const [activeTab, setActiveTab] = useState<'lexical' | 'syntax' | null>(null);
  const [analysisResult, setAnalysisResult] = useState<{
    lexicalResult: any[];
    syntaxResult: any;
  } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const analyseSql = async () => {
    setActiveTab(null); // Сбрасываем активную вкладку
    setError(null); // Сбрасываем ошибку перед новой попыткой
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
      setAnalysisResult(result); // Устанавливаем результат анализа
      setError(null); // Очищаем ошибку при успешном анализе
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred'); // Устанавливаем ошибку
      setAnalysisResult(null); // Сбрасываем результат анализа при ошибке
    }
  };

  return (
      <div className="min-h-screen bg-gray-50">
        <div className="mx-auto p-6">
          <div className="text-center mb-8">
            <h1 className="text-3xl font-bold text-gray-800 mb-2">SQL to MongoDB Translator</h1>
            <p className="text-gray-600">Analyze and convert your SQL queries to MongoDB format</p>
          </div>

          <div className="flex gap-6 h-[calc(100vh-180px)] w-full">
            {/* Левый блок ввода */}
            <div className={`bg-white rounded-lg shadow-md overflow-hidden ${activeTab || error ? 'w-1/2' : 'w-full'} transition-all duration-300`}>
              <div className="p-6 border-b border-gray-200 flex gap-3">
                <button
                    onClick={analyseSql}
                    className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"
                >
                  <FileSearch size={20} />
                  Analyze
                </button>
                <button
                    onClick={() => analysisResult && setActiveTab('lexical')}
                    className={`flex items-center gap-2 px-4 py-2 rounded-md transition-colors ${
                        analysisResult && activeTab === 'lexical'
                            ? 'bg-green-600 text-white'
                            : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                    }`}
                    disabled={!analysisResult} // Блокируем кнопку, если нет результата анализа
                >
                  <Code2 size={20} />
                  Lexical Analysis
                </button>
                <button
                    onClick={() => analysisResult && setActiveTab('syntax')}
                    className={`flex items-center gap-2 px-4 py-2 rounded-md transition-colors ${
                        analysisResult && activeTab === 'syntax'
                            ? 'bg-purple-600 text-white'
                            : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                    }`}
                    disabled={!analysisResult} // Блокируем кнопку, если нет результата анализа
                >
                  <Database size={20} />
                  Syntax Analysis
                </button>
              </div>
              <div className="h-[calc(100%-68px)] overflow-auto p-6">
              <textarea
                  id="sqlQuery"
                  value={sqlQuery}
                  onChange={(e) => setSqlQuery(e.target.value)}
                  className="w-full h-full min-h-[300px] px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono text-sm"
                  placeholder="Enter your SQL query here..."
              />
              </div>
            </div>

            {/* Правый блок результатов или ошибок */}
            {(activeTab || error) && (
                <div className="w-1/2 bg-white rounded-lg shadow-md overflow-hidden flex flex-col">
                  <div className="p-6 border-b border-gray-200 flex justify-between items-center">
                    <h2 className="text-lg font-semibold text-gray-800">
                      {error ? 'Error' : activeTab === 'lexical' ? 'Lexical Analysis' : 'Syntax Analysis'}
                    </h2>
                    <button
                        onClick={() => {
                          setActiveTab(null);
                          setError(null); // Сбрасываем ошибку при закрытии
                        }}
                        className="text-gray-500 hover:text-gray-700"
                    >
                      ×
                    </button>
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
                              <th className="px-4 py-2 sticky left-0 bg-white">#</th>
                              <th className="px-4 py-2 min-w-[200px]">Lexeme</th>
                              <th className="px-4 py-2 min-w-[200px]">Category</th>
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
                        <SyntaxTree data={analysisResult.syntaxResult} />
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